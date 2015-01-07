import praw
from bitcoin.base58 import decode
import json
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
from cate.blockchain import *
from cate.fees import CFeeRate
from cate.script import extract_secret_from_receive_script
from cate.tx import build_receive_tx

def find_secret_from_peer_receive_tx(proxy, audit,  own_send_tx):
  """
  Gets the secret value from the transaction spending TX3
  """
  own_send_tx_vout = COutPoint(own_send_tx.GetHash(), 0)

  # Scan the mempool first
  for txid in proxy.getrawmempool():
    recv_tx = proxy.getrawtransaction(txid)
    recv_tx_in = find_txin_matching_output(recv_tx, own_send_tx_vout)
    if recv_tx_in:
      return extract_secret_from_receive_script(recv_tx, recv_tx_in)

  # Scan the blockchain
  statbuf = os.stat(audit.get_path('5_tx4.txt'))
  (height, block) = get_first_block(proxy, statbuf.st_mtime)
  height += 1
  recv_tx = find_tx_matching_output(block, own_send_tx_vout)
  while not recv_tx:
    try:
      block = proxy.getblock(proxy.getblockhash(height))
      height += 1
    except IndexError as err:
      time.sleep(5)
      continue
    recv_tx = find_tx_matching_output(block, own_send_tx_vout)

  recv_tx_in = find_txin_matching_output(recv_tx, own_send_tx_vout)

  return extract_secret_from_receive_script(recv_tx, recv_tx_in)

def spend_peer_send_tx(peer_send_tx_id, trade_id):
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
  print "Waiting for TX spending " + b2lx(tx3.GetHash())
  secret = find_secret_from_peer_receive_tx(proxy, audit, tx3)

  # Connect to the wallet we're receiving coins from
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])
  fee_rate = CFeeRate(config['daemons'][ask_currency_code]['fee_per_kb'])

  # Get an address to pull the funds into
  own_address = proxy.getnewaddress("CATE " + trade_id)

  # Get TX1 from the blockchain
  peer_send_tx = proxy.getrawtransaction(peer_send_tx_id)

  # Create a new transaction spending TX1, using the secret and our private key
  own_receive_tx = build_receive_tx(proxy, peer_send_tx, private_key_b, secret, own_address, fee_rate)

  # Send the transaction to the blockchain
  bitcoin.core.scripteval.VerifyScript(own_receive_tx.vin[0].scriptSig, peer_send_tx.vout[0].scriptPubKey, own_receive_tx, 0, (SCRIPT_VERIFY_P2SH,))
  try:
    proxy.sendrawtransaction(own_receive_tx)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -25:
      print "Send transaction " + b2lx(peer_send_tx_id) + " for trade " + trade_id + " has already been spent"
    else:
      raise err
  ready_transactions.pop(peer_send_tx_id, None)

  # Add a file to indicate the TX is complete
  audit.save_text('7_complete.txt', b2lx(own_receive_tx.GetHash()))

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
  peer_recovery_tx = CTransaction.deserialize(x(acceptance['tx2']))
  ready_transactions[peer_recovery_tx.vin[0].prevout.hash] = trade_id

while ready_transactions:
  peer_send_tx_ids = ready_transactions.keys()
  for peer_send_tx_id in peer_send_tx_ids:
    trade_id = ready_transactions[peer_send_tx_id]
    spend_peer_send_tx(peer_send_tx_id, trade_id)
  if ready_transactions:
    time.sleep(5)
