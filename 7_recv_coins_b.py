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
from cate.error import ConfigurationError
from cate.fees import CFeeRate
from cate.tx import build_tx1_tx3_spend

def find_txin_with_in(proxy, block, vout):
  """
  Searches the given block for a transaction with the input provided
  
  Returns the TX input, or None if no match found
  """
  for tx in block.vtx:
    for inpoint in tx.vin:
      prevout = inpoint.prevout
      if prevout.hash == vout.hash and prevout.n == vout.n:
        return inpoint
  return None

def get_first_block(proxy, audit_directory):
  """
  Estimates the first block which could reasonably contain the transaction
  spending TX3
  
  Returns a tuple of block height and the first block
  """

  statbuf = os.stat(audit_directory + os.path.sep + '5_tx4.txt')
  
  blockchain_info = proxy.getblockchaininfo()
  
  # Figure out approximate block interval by grabbing the last block, and ten blocks ago
  height = blockchain_info['blocks']
  prev_height = max(0, height - 10)
  block_top = proxy.getblock(lx(blockchain_info['bestblockhash']))
  block_prev = proxy.getblock(proxy.getblockhash(prev_height))

  block_time = (block_top.nTime - block_prev.nTime) / (height - prev_height)
  blocks_to_go_back = min(height, int(math.ceil((block_top.nTime - statbuf.st_mtime) / block_time)))
  first_height = height - blocks_to_go_back

  first_block = proxy.getblock(proxy.getblockhash(first_height))
  while first_block.nTime > statbuf.st_mtime:
    first_height -= 1
    first_block = proxy.getblock(proxy.getblockhash(first_height))

  return (first_height, first_block)

def get_tx1_id(directory_path):
  """
  Load TX2 from disk, and extract TX1's ID from its inputs
  """
  with open(directory_path + os.path.sep + '3_acceptance.json', "r") as confirmation_file:
    confirmation = json.loads(confirmation_file.read())
  tx2 = CTransaction.deserialize(x(confirmation['tx2']))
  return tx2.vin[0].prevout.hash

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
  if not os.path.isdir(directory_path):
    continue
  if not os.path.isfile(directory_path + os.path.sep + '5_tx4.txt'):
    continue
  if os.path.isfile(directory_path + os.path.sep + '7_complete.txt'):
    continue
  tx1_id = get_tx1_id(directory_path)
  ready_transactions[tx1_id] = trade_id

while ready_transactions:
  tx1ids = ready_transactions.keys()
  for tx1_id in tx1ids:
    trade_id = ready_transactions[tx1_id]
    print "Spending coins from trade " + trade_id
    audit_directory = 'audits' + os.path.sep + trade_id

    with open(audit_directory + os.path.sep + '1_offer.json', "r") as offer_file:
      offer = json.loads(offer_file.read())
    offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
    ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
    ask_currency_quantity = offer['ask_currency_quantity']
    offer_currency_quantity = offer['offer_currency_quantity']

    # Extract the private key from the audit directory
    if not os.path.isfile(audit_directory + os.path.sep + '1_private_key.txt'):
      print "Missing private_key file for trade ID " + trade_id
      ready_transactions.pop(tx1_id, None)
      continue
    with open(audit_directory + os.path.sep + '1_private_key.txt', "r") as private_key_file:
      private_key_a = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(x(private_key_file.read()), True)

    if not os.path.isfile(audit_directory + os.path.sep + '3_tx3.txt'):
      print "Missing TX3 file for trade ID " + trade_id
      ready_transactions.pop(tx1_id, None)
      continue
    with open(audit_directory + os.path.sep + '3_tx3.txt', "r") as tx3_file:
      tx3 = CTransaction.deserialize(x(tx3_file.read()))

    # Connect to the wallet we've sent coins to
    bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
    proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])

    # Monitor the other block chain for a transaction spending the outputs from TX3
    (height, block) = get_first_block(proxy, audit_directory)
    height += 1
    tx3_vin = find_txin_with_in(proxy, block, COutPoint(tx3.GetHash(), 0))
    while not tx3_vin:
      try:
        block = proxy.getblock(proxy.getblockhash(height))
        height += 1
      except IndexError as err:
        time.sleep(5)
        continue
      tx3_vin = find_txin_with_in(proxy, block, COutPoint(tx3.GetHash(), 0))

    # Extract the secret from the transaction output script
    sig_elements = []
    for sig_element in tx3_vin.scriptSig:
      sig_elements.append(sig_element)
    if len(sig_elements) < 3:
      # TODO: we should keep a note of which transaction this is, so we can
      # report the TX ID
      raise TradeError("Cannot extract shared secret from scriptSig of transaction.")
    secret = sig_elements[2]

    # Connect to the wallet we're receiving coins from
    bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
    proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])
    fee_rate = CFeeRate(config['daemons'][ask_currency_code]['fee_per_kb'])

    # Get an address to pull the funds into
    own_address = proxy.getnewaddress("CATE " + trade_id)
    
    # Get TX1 from the blockchain
    tx1 = proxy.getrawtransaction(tx1_id)

    # Create a new transaction spending TX1, using the secret and our private key
    tx_spend = build_tx1_tx3_spend(proxy, tx1, private_key_a, secret, own_address, fee_rate)

    # Send the transaction to the blockchain
    print tx1
    print tx_spend
    bitcoin.core.scripteval.VerifyScript(tx_spend.vin[0].scriptSig, tx1.vout[0].scriptPubKey, tx_spend, 0, (SCRIPT_VERIFY_P2SH,))
    proxy.sendrawtransaction(tx_spend)
    ready_transactions.pop(tx1_id, None)

    # Add a file to indicate the TX is complete
    with open(audit_directory + os.path.sep + '7_complete.txt', "w", 0700) as completion_file:
      completion_file.write(tx_spend.GetHash())

  if ready_transactions:
    time.sleep(5)
