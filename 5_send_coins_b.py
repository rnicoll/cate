import praw
import hashlib
import json
import os.path
import socket
import sys

from bitcoin import SelectParams
from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.core.scripteval
import bitcoin.rpc
from cate import *
from cate.blockchain import *
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

def process_offer_confirmed(send_notification, audit):
  """
  1. Receives the completed TX2 and the partial TX4 from 'B'.
  2. Verifies the signatures on TX2 are valid
  3. Verifies the lock time on TX4 is valid
  4. Signs TX4 to complete it
  5. Returns TX4 to 'B'
  6. Submits TX1 to the network
  """

  offer = audit.load_json('1_offer.json')
  acceptance = audit.load_json('3_acceptance.json')
  confirmation = audit.load_json('3_confirmation.json')

  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  private_key_b = audit.load_private_key('1_private_key.txt')
  public_key_a = bitcoin.core.key.CPubKey(x(acceptance['public_key_a']))
  public_key_b = bitcoin.core.key.CPubKey(private_key_b._cec_key.get_pubkey())
  secret_hash = x(acceptance['secret_hash'])

  tx2 = CTransaction.deserialize(x(acceptance['tx2']))
  tx3 = audit.load_tx('3_tx3.txt')
  tx4 = CMutableTransaction.from_tx(CTransaction.deserialize(x(confirmation['tx4'])))

  # Apply signatures to TX4 and check the result is valid
  txin_scriptPubKey = tx3.vout[0].scriptPubKey
  sighash = SignatureHash(txin_scriptPubKey, tx4, 0, SIGHASH_ALL)
  tx4_sig_a = x(send_notification['tx4_sig'])
  tx4_sig_b = private_key_b.sign(sighash) + (b'\x01') # Append signature hash type
  if not public_key_a.verify(sighash, tx4_sig_a):
    raise TradeError("Signature from peer for TX4 is invalid.")

  tx4.vin[0].scriptSig = CScript([tx4_sig_b, public_key_b, 1, tx4_sig_a, public_key_a])
  bitcoin.core.scripteval.VerifyScript(tx4.vin[0].scriptSig, txin_scriptPubKey, tx4, 0, (SCRIPT_VERIFY_P2SH,))
  audit.save_tx('5_tx4.txt', tx4)

  # Check TX1 has been confirmed
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])
  statbuf = os.stat(audit.get_path('3_tx3.txt'))
  print "Waiting for TX " + b2lx(tx2.vin[0].prevout.hash) + " to confirm"
  wait_for_tx_to_confirm(proxy, audit, tx2.vin[0].prevout.hash, statbuf.st_mtime)

  # Relay TX3
  bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])
  proxy.sendrawtransaction(tx3)

  return True

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(0)

r = praw.Reddit(user_agent = USER_AGENT)
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
  audit = TradeDao(trade_id)

  if audit.file_exists('5_send_notification.json'):
    print "Offer send_notification " + trade_id + " already received, ignoring offer"
    continue

  # Record the received response
  audit.save_json('5_send_notification.json', send_notification)

  try:
    response = process_offer_confirmed(send_notification, audit)
  except socket.error as err:
    print "Could not connect to wallet."
    sys.exit(1)
  if not response:
    break

  # The remote side should be scanning for TX3, which it will then spend,
  # giving us the key to TX1. We then spend TX1.
