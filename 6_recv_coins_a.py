import json
import os.path
import socket
from StringIO import StringIO
import sys
import time

from bitcoin import SelectParams
from bitcoin.core import *
import bitcoin.rpc
from cate import *
from cate.error import ConfigurationError
from cate.fees import CFeeRate
from cate.tx import *

def get_tx3_id(directory_path):
  """
  Load TX4 from disk, and extract TX3's ID from its inputs
  """
  with open(directory_path + os.path.sep + '4_confirmation.json', "r") as confirmation_file:
    confirmation = json.loads(confirmation_file.read())
  tx4 = CTransaction.deserialize(x(confirmation['tx4']))
  return tx4.vin[0].prevout.hash

# This is where both coins have been sent to the blockchains and 'A'
# can now use the secret to spend the coins sent by 'B'

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
  if not os.path.isfile(directory_path + os.path.sep + '4_coins_sent.json'):
    continue
  tx3_id = get_tx3_id(directory_path)
  ready_transactions[tx3_id] = trade_id

while ready_transactions:
  tx3ids = ready_transactions.keys()
  for tx3_id in tx3ids:
    audit_directory = 'audits' + os.path.sep + trade_id
    trade_id = ready_transactions[tx3_id]

    with open(audit_directory + os.path.sep + '2_offer.json', "r") as offer_file:
      offer = json.loads(offer_file.read())
    offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
    ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
    ask_currency_quantity = offer['ask_currency_quantity']
    offer_currency_quantity = offer['offer_currency_quantity']

    # Extract the private key from the audit directory
    if not os.path.isfile(audit_directory + os.path.sep + '2_private_key.txt'):
      print "Missing private_key file for trade ID " + trade_id
      ready_transactions.pop(tx3_id, None)
      continue
    with open(audit_directory + os.path.sep + '2_private_key.txt', "r") as private_key_file:
      private_key_a = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(x(private_key_file.read()), True)

    # Extract the secret from the audit directory
    if not os.path.isfile(audit_directory + os.path.sep + '2_secret.txt'):
      print "Missing secret file for trade ID " + trade_id
      ready_transactions.pop(tx3_id, None)
      continue
    # Extract the secret from the audit directory
    with open(audit_directory + os.path.sep + '2_secret.txt', "r") as secret_file:
      secret = x(secret_file.read())

    # Connect to the wallet
    bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
    proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])
    fee_rate = CFeeRate(config['daemons'][offer_currency_code]['fee_per_kb'])

    # Monitor the block chain for TX3 being relayed
    try:
      tx_details = proxy.gettransaction(tx3_id)
    except IndexError as err:
      # Transaction is not yet ready
      continue
    if tx_details['confirmations'] == 0:
      continue

    # Fetch the original transaction
    tx3 = proxy.getrawtransaction(tx3_id)

    # FIXME: Verify the secret we have matches the one expected
    print Hash(secret)

    # Get an address to pull the funds into
    own_address = proxy.getnewaddress("CATE " + trade_id)

    # Create a new transaction spending TX3, using the secret and our private key
    spend_tx = build_tx1_tx3_spend(proxy, tx3, private_key_a, secret, own_address, fee_rate)

    # Send the transaction to the blockchain
    print spend_tx
    proxy.sendrawtransaction(spend_tx)
    ready_transactions.pop(tx3_id, None)
  if ready_transactions:
    time.sleep(5)
