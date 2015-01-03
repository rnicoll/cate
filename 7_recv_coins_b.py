import praw
from bitcoin.base58 import decode
from decimal import Decimal
import hashlib
import json
import os.path
import socket
from StringIO import StringIO
import sys

from bitcoin import SelectParams
from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.rpc
from cate import *
from cate.error import ConfigurationError

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
  tx1_id = get_tx1_id(directory_path)
  ready_transactions[tx1_id] = trade_id

while ready_transactions:
  tx1ids = ready_transactions.keys()
  for tx1_id in tx1ids:
    trade_id = ready_transactions[tx1_id]
    audit_directory = 'audits' + os.path.sep + trade_id

    with open(audit_directory + os.path.sep + '1_offer.json', "r") as offer_file:
      offer = json.loads(offer_file.read())
    offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
    ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
    ask_currency_quantity = offer['ask_currency_quantity']
    offer_currency_quantity = offer['offer_currency_quantity']

    # Connect to the wallet we've sent coins to
    bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
    proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])

    # TODO: Monitor the other block chain for a transaction spending the outputs from
    # TX3
    
    # Extract the secret from the transaction output script

    # Extract the private key from the audit directory
    if not os.path.isfile(audit_directory + os.path.sep + '1_private_key.txt'):
      print "Missing private_key file for trade ID " + trade_id
      ready_transactions.pop(tx1_id, None)
      continue
    with open(audit_directory + os.path.sep + '1_private_key.txt', "r") as private_key_file:
      private_key_a = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(x(private_key_file.read()), True)

    # Connect to the wallet we're receiving coins from
    bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
    proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])
    fee_rate = CFeeRate(config['daemons'][ask_currency_code]['fee_per_kb'])

    # Get an address to pull the funds into
    own_address = proxy.getnewaddress("CATE " + trade_id)

    # Create a new transaction spending TX1, using the secret and our private key
    tx_spend = build_tx1_tx3_spend(proxy, tx1, private_key_a, secret, own_address, fee_rate)

    # Send the transaction to the blockchain
    print tx1
    print tx_spend
    bitcoin.core.scripteval.VerifyScript(tx_spend.vin[0].scriptSig, tx1.vout[0].scriptPubKey, tx_spend, 0, (SCRIPT_VERIFY_P2SH,))
    proxy.sendrawtransaction(tx_spend)
    ready_transactions.pop(tx1_id, None)
  if ready_transactions:
    time.sleep(5)