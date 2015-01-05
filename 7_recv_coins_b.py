import praw
from bitcoin.base58 import decode
import json
import math
import os.path
import socket
import sys
import time

from bitcoin import SelectParams
from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.core.scripteval
import bitcoin.rpc
from cate import *
#from cate.error import ConfigurationError
from cate.fees import CFeeRate
from cate.tx import build_tx1_tx3_spend, extract_secret_from_vin

def find_txin_matching_output(tx, vout):
  """
  Searches the given block for a transaction with the input provided

  Returns the TX if a match is found, or None otherwise
  """
  for inpoint in tx.vin:
    prevout = inpoint.prevout
    if prevout.hash == vout.hash and prevout.n == vout.n:
      return inpoint
  return None

def find_tx_matching_output(block, vout):
  """
  Searches the given block for a transaction with the input provided
  
  Returns the TX if a match is found, or None otherwise
  """
  for tx in block.vtx:
    txin = find_txin_matching_output(tx, vout)
    if txin:
      return tx
  return None

def get_first_block(proxy, audit):
  """
  Estimates the first block which could reasonably contain the transaction
  spending TX3
  
  Returns a tuple of block height and the first block
  """
  statbuf = os.stat(audit.get_path('5_tx4.txt'))
  
  blockchain_info = proxy.getblockchaininfo()
  
  # Figure out approximate block interval by grabbing the last block, and ten blocks ago
  height = blockchain_info['blocks']
  prev_height = max(0, height - 10)
  block_top = proxy.getblock(lx(blockchain_info['bestblockhash']))
  block_prev = proxy.getblock(proxy.getblockhash(prev_height))

  block_time = (block_top.nTime - block_prev.nTime) / (height - prev_height)
  if block_top.nTime > statbuf.st_mtime:
    blocks_to_go_back = min(height, int(math.ceil((block_top.nTime - statbuf.st_mtime) / block_time)))
  else:
    # No blocks since the transaction completed, so we start at the top of the chain
    blocks_to_go_back = 0
  first_height = height - blocks_to_go_back

  first_block = proxy.getblock(proxy.getblockhash(first_height))
  while first_block.nTime > statbuf.st_mtime:
    first_height -= 1
    first_block = proxy.getblock(proxy.getblockhash(first_height))

  return (first_height, first_block)

def spend_tx1(tx1_id, trade_id):
  print "Spending coins from trade " + trade_id

  audit = TradeDao(trade_id)
  offer = audit.load_json('1_offer.json')
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  private_key_b = audit.load_private_key('1_private_key.txt')
  tx3 = audit.load_tx('3_tx3.txt')

  # Connect to the wallet we've sent coins to
  bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])

  # Monitor the other block chain for a transaction spending the outputs from TX3
  (height, block) = get_first_block(proxy, audit)
  height += 1
  tx3_vout = COutPoint(tx3.GetHash(), 0)
  tx3_spend = find_tx_matching_output(block, tx3_vout)
  while not tx3_spend:
    try:
      block = proxy.getblock(proxy.getblockhash(height))
      height += 1
    except IndexError as err:
      time.sleep(5)
      continue
    tx3_spend = find_tx_matching_output(block, tx3_vout)

  tx3_in = find_txin_matching_output(tx3_spend, tx3_vout)
  secret = extract_secret_from_vin(tx3_spend, tx3_in)

  # Connect to the wallet we're receiving coins from
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])
  fee_rate = CFeeRate(config['daemons'][ask_currency_code]['fee_per_kb'])

  # Get an address to pull the funds into
  own_address = proxy.getnewaddress("CATE " + trade_id)

  # Get TX1 from the blockchain
  tx1 = proxy.getrawtransaction(tx1_id)

  # Create a new transaction spending TX1, using the secret and our private key
  tx1_spend = build_tx1_tx3_spend(proxy, tx1, private_key_b, secret, own_address, fee_rate)

  # Send the transaction to the blockchain
  bitcoin.core.scripteval.VerifyScript(tx1_spend.vin[0].scriptSig, tx1.vout[0].scriptPubKey, tx1_spend, 0, (SCRIPT_VERIFY_P2SH,))
  try:
    proxy.sendrawtransaction(tx1_spend)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -25:
      print "TX1 for trade " + trade_id + " has already been spent"
    else:
      raise err
  ready_transactions.pop(tx1_id, None)

  # Add a file to indicate the TX is complete
  audit.save_text('7_complete.txt', b2lx(tx1_spend.GetHash()))

# This is the final step under normal circumstances, in which 'B' extracts
# the secret from the transaction 'A' sent to spend the coins provided.

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(0)

# Scan the audit directory for transactions ready to spend
ready_transactions = {}
for trade_id in os.listdir('audits'):
  directory_path = 'audits' + os.path.sep + trade_id
  audit = TradeDao(trade_id)
  if not audit.file_exists('5_tx4.txt'):
    continue
  if audit.file_exists('7_complete.txt'):
    continue
  acceptance = audit.load_json('3_acceptance.json')
  tx2 = CTransaction.deserialize(x(acceptance['tx2']))
  ready_transactions[tx2.vin[0].prevout.hash] = trade_id

while ready_transactions:
  tx1ids = ready_transactions.keys()
  for tx1_id in tx1ids:
    trade_id = ready_transactions[tx1_id]
    spend_tx1(tx1_id, trade_id)
  if ready_transactions:
    time.sleep(5)
