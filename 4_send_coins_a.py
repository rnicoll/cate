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
import bitcoin.rpc
import bitcoin.core.scripteval
from cate import *
from cate.error import *
from cate.tx import *

def assert_confirmation_valid(confirmation):
  if 'trade_id' not in confirmation:
    raise MessageError( "Missing trade ID from confirmed offer.")
  if confirmation['trade_id'].find(os.path.sep) != -1:
    raise MessageError("Invalid trade ID received; trade must not contain path separators")
  if 'tx2_sig' not in confirmation:
    raise MessageError( "Missing TX2 signature from confirmed offer.")
  if 'tx4' not in confirmation:
    raise MessageError( "Missing TX4 refund transaction from confirmed offer.")

def process_offer_confirmed(confirmation, audit_directory):
  """
  1. Receives the completed TX2 and the partial TX4 from 'B'.
  2. Verifies the signatures on TX2 are valid
  3. Verifies the lock time on TX4 is valid
  4. Signs TX4 to complete it
  5. Returns TX4 to 'B'
  6. Submits TX1 to the network
  """

  with open(audit_directory + os.path.sep + '2_offer.json', "r") as offer_file:
    offer = json.loads(offer_file.read())
  with open(audit_directory + os.path.sep + '2_acceptance.json', "r") as acceptance_file:
    acceptance = json.loads(acceptance_file.read())

  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  ask_currency_quantity = offer['ask_currency_quantity']
  offer_currency_quantity = offer['offer_currency_quantity']
  with open(audit_directory + os.path.sep + '2_secret.txt', "r") as secret_file:
    a_private_key = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(x(secret_file.read()), True)
  a_public_key = bitcoin.core.key.CPubKey(a_private_key._cec_key.get_pubkey())
  b_public_key = bitcoin.core.key.CPubKey(x(offer['b_public_key']))
  secret_hash = x(acceptance['secret_hash'])

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  with open(audit_directory + os.path.sep + '2_tx1.txt', "r") as tx1_file:
    tx1 = CTransaction.deserialize(x(tx1_file.read()))
  tx2 = CMutableTransaction.deserialize(x(acceptance['tx2']))
  tx4 = CMutableTransaction.deserialize(x(confirmation['tx4']))

  print a_public_key
  print b_public_key
  print b2x(Hash(a_public_key))
  print b2x(Hash(b_public_key))
  
  print ""
  print tx1


  # Apply signatures to TX2 and check it is a valid TX
  b_tx2_sig = x(confirmation['tx2_sig'])
  # TODO: verify the signature here
  tx2 = sign_tx2(proxy, tx2, a_private_key, a_public_key, b_public_key, secret_hash, b_tx2_sig)
  print ""
  print tx2
  bitcoin.core.scripteval.VerifyScript(tx2.vin[0].scriptSig, tx1.vout[0].scriptPubKey, tx2, 0, (SCRIPT_VERIFY_P2SH,))

  # Verify the TX4 returned by the peer, then sign it
  assert_tx2_valid(tx4)
  sign_tx2(proxy, tx4, own_address, peer_address, secret_hash)

  # proxy.sendrawtransaction(tx1)

  # Pass back the signature
  return {
    'trade_id': trade_id,
    'tx4': b2x(tx4.serialize())
  }

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(0)

r = praw.Reddit(user_agent = "CATE - Cross-chain Atomic Trading Engine")
try:
  reddit_login(r, config)
except ConfigurationError as e:
  print e
  sys.exit(0)

for message in r.get_messages():
  if message.subject != 'CATE transaction confirmed (3)':
    break
  confirmation = json.loads(message.body)
  assert_confirmation_valid(confirmation)
  trade_id = confirmation['trade_id']
  audit_directory = ensure_audit_directory_exists(trade_id)

  audit_filename = audit_directory + os.path.sep + '4_confirmation.json'
  if os.path.isfile(audit_filename):
    print "Offer confirmation " + trade_id + " already received, ignoring offer"
    continue

  # Record the received response
  with open(audit_directory + os.path.sep + '4_confirmation.json', "w", 0700) as response_file:
    io = StringIO()
    json.dump(confirmation, io, indent=4, separators=(',', ': '))
    response_file.write(io.getvalue())

  response = process_offer_confirmed(confirmation, audit_directory)
  if not response:
    break

  io = StringIO()
  json.dump(response, io)

  # Record the message
  with open(audit_directory + os.path.sep + '4_coins_sent.json', "w", 0700) as response_file:
    response_file.write(io.getvalue())

  # r.send_message(other_redditor, 'CATE transaction sent (4)', io.getvalue())