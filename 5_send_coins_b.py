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
import bitcoin.core.scripteval
import bitcoin.rpc
from cate import *
from cate.error import ConfigurationError, MessageError, TradeError
from cate.tx import *

# 'B' receives the completed TX4 from 'A', verifies the signatures on it
# are valid, then submits TX3 to the network

def assert_send_notification_valid(send_notification):
  if 'trade_id' not in send_notification:
    raise MessageError( "Missing trade ID from confirmed offer.")
  if send_notification['trade_id'].find(os.path.sep) != -1:
    raise MessageError("Invalid trade ID received; trade must not contain path separators")
  if 'tx4_sig' not in send_notification:
    raise MessageError( "Missing TX4 signature from confirmed offer.")

def process_offer_confirmed(send_notification, audit_directory):
  """
  1. Receives the completed TX2 and the partial TX4 from 'B'.
  2. Verifies the signatures on TX2 are valid
  3. Verifies the lock time on TX4 is valid
  4. Signs TX4 to complete it
  5. Returns TX4 to 'B'
  6. Submits TX1 to the network
  """

  with open(audit_directory + os.path.sep + '1_offer.json', "r") as offer_file:
    offer = json.loads(offer_file.read())
  with open(audit_directory + os.path.sep + '3_acceptance.json', "r") as acceptance_file:
    acceptance = json.loads(acceptance_file.read())
  with open(audit_directory + os.path.sep + '3_confirmation.json', "r") as confirmation_file:
    confirmation = json.loads(confirmation_file.read())

  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  ask_currency_quantity = offer['ask_currency_quantity']
  offer_currency_quantity = offer['offer_currency_quantity']
  with open(audit_directory + os.path.sep + '1_private_key.txt', "r") as secret_file:
    private_key_b = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(x(secret_file.read()), True)
  public_key_a = bitcoin.core.key.CPubKey(x(acceptance['public_key_a']))
  public_key_b = bitcoin.core.key.CPubKey(private_key_b._cec_key.get_pubkey())
  secret_hash = x(acceptance['secret_hash'])

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])

  with open(audit_directory + os.path.sep + '3_tx3.txt', "r") as tx3_file:
    tx3 = CTransaction.deserialize(x(tx3_file.read()))
  tx4 = CMutableTransaction.from_tx(CTransaction.deserialize(x(confirmation['tx4'])))

  # Apply signatures to TX2 and check the result is valid
  txin_scriptPubKey = tx3.vout[0].scriptPubKey
  sighash = SignatureHash(txin_scriptPubKey, tx4, 0, SIGHASH_ALL)
  tx4_sig_a = x(send_notification['tx4_sig'])
  tx4_sig_b = private_key_b.sign(sighash) + (b'\x01') # Append signature hash type
  if not public_key_a.verify(sighash, tx4_sig_a):
    raise TradeError("Signature from peer for TX4 is invalid.")

  tx4.vin[0].scriptSig = CScript([OP_0, tx4_sig_b, tx4_sig_a, 2, public_key_b, public_key_a, 2])
  bitcoin.core.scripteval.VerifyScript(tx4.vin[0].scriptSig, txin_scriptPubKey, tx4, 0, (SCRIPT_VERIFY_P2SH,))
  with open(audit_directory + os.path.sep + '5_tx4.txt', "w") as tx4_file:
    tx4_file.write(b2x(tx4.serialize()))

  proxy.sendrawtransaction(tx3)

  return True

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
  if message.subject != 'CATE transaction sent (4)':
    break
  send_notification = json.loads(message.body)
  try:
    assert_send_notification_valid(send_notification)
  except MessageError as err:
    print("Received invalid trade from " + message.author.name)
    continue
  trade_id = send_notification['trade_id']
  audit_directory = ensure_audit_directory_exists(trade_id)

  audit_filename = audit_directory + os.path.sep + '5_send_notification.json'
  if os.path.isfile(audit_filename):
    print "Offer send_notification " + trade_id + " already received, ignoring offer"
    continue

  # Record the received response
  with open(audit_directory + os.path.sep + '5_send_notification.json', "w", 0700) as response_file:
    io = StringIO()
    json.dump(send_notification, io, indent=4, separators=(',', ': '))
    response_file.write(io.getvalue())

  response = process_offer_confirmed(send_notification, audit_directory)
  if not response:
    break

  # The remote side should be scanning for TX3, which it will then spend,
  # giving us the key to TX1. We then spend TX1.
