import json
import os.path
import socket
import sys
import time

from altcoin import SelectParams
import altcoin.rpc
import bitcoin.core.scripteval
import bitcoin.rpc

from cate import *
from cate.blockchain import *
from cate.error import ConfigurationError
from cate.fees import CFeeRate
from cate.tx import *

def spend_peer_send_tx(peer_send_tx_id, trade_id):
  """
  Spends the coins from the trade sent the remote peer, to an address we control.
  """

  print ("Spending coins from trade " + trade_id)
  audit = TradeDao(trade_id)

  offer = audit.load_json('2_offer.json')
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  private_key_a = audit.load_private_key('2_private_key.txt')
  secret = audit.load_secret('2_secret.txt')

  # Connect to the wallet
  altcoin.SelectParams(offer['offer_currency_hash'])
  proxy = altcoin.rpc.AltcoinProxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])
  fee_rate = CFeeRate(config['daemons'][offer_currency_code]['fee_per_kb'])

  # Monitor the block chain for TX3 being relayed
  statbuf = os.stat(audit.get_path('4_tx2.txt'))
  print ("Waiting for TX " )+ b2lx(peer_send_tx_id) + " to confirm"
  peer_send_tx = wait_for_tx_to_confirm(proxy, audit, peer_send_tx_id, statbuf.st_mtime)

  # TODO: Verify the secret we have matches the one expected; this is covered by
  # verify script later, but good to check here too

  # Get an address to pull the funds into
  own_address = proxy.getnewaddress("CATE " + trade_id)

  # Create a new transaction spending TX3, using the secret and our private key
  own_receive_tx = build_receive_tx(proxy, peer_send_tx, private_key_a, secret, own_address, fee_rate)

  # Send the transaction to the blockchain
  bitcoin.core.scripteval.VerifyScript(own_receive_tx.vin[0].scriptSig, peer_send_tx.vout[0].scriptPubKey, own_receive_tx, 0, (SCRIPT_VERIFY_P2SH,))
  try:
    proxy.sendrawtransaction(own_receive_tx)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -25:
      print ("Send transaction " + b2lx(peer_send_tx_id) + " for trade " + trade_id + " has already been spent")
    else:
      raise err
  ready_transactions.pop(peer_send_tx_id, None)

  # Add a file to indicate the TX is complete
  audit.save_text('6_complete.txt', b2lx(own_receive_tx.GetHash()))

# This is where both coins have been sent to the blockchains and 'A'
# can now use the secret to spend the coins sent by 'B'

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print (e)
  sys.exit(0)

# Scan the audit directory for transactions ready to spend
ready_transactions = {}
for trade_id in os.listdir('audits'):
  audit = TradeDao(trade_id)
  if not audit.file_exists('4_coins_sent.json'):
    continue
  if audit.file_exists('6_complete.txt'):
    continue
  confirmation = audit.load_json('4_confirmation.json')
  tx4 = CTransaction.deserialize(x(confirmation['tx4']))

  # Record a mapping from the TX3 ID to the trade ID
  ready_transactions[tx4.vin[0].prevout.hash] = trade_id

while ready_transactions:
  send_tx_ids = ready_transactions.keys()
  for send_tx_id in send_tx_ids:
    trade_id = ready_transactions[send_tx_id]
    spend_peer_send_tx(send_tx_id, trade_id)
  if ready_transactions:
    time.sleep(5)
