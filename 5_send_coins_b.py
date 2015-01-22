import praw
import json
import os.path
import socket
import sys

from altcoin import SelectParams
import altcoin.rpc
import bitcoin.core.scripteval

from cate import *
from cate.blockchain import *
from cate.error import ConfigurationError, MessageError, TradeError
from cate.script import *
from cate.tx import *

# Peer 'B' receives the completed refund transaction from 'A', verifies the
# signatures on it are valid, verifies 'A' has sent the coins it agreed to
# then relays the transaction containing its own coins to the network

def assert_send_notification_valid(send_notification):
  if 'trade_id' not in send_notification:
    raise MessageError( "Missing trade ID from confirmed offer.")
  if send_notification['trade_id'].find(os.path.sep) != -1:
    raise MessageError("Invalid trade ID received; trade must not contain path separators")
  if 'tx4_sig' not in send_notification:
    raise MessageError( "Missing refund transaction signature from confirmed offer.")

def process_offer_confirmed(send_notification, audit):
  offer = audit.load_json('1_offer.json')
  acceptance = audit.load_json('3_acceptance.json')
  confirmation = audit.load_json('3_confirmation.json')

  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  private_key_b = audit.load_private_key('1_private_key.txt')
  public_key_a = bitcoin.core.key.CPubKey(x(acceptance['public_key_a']))
  public_key_b = bitcoin.core.key.CPubKey(private_key_b._cec_key.get_pubkey())
  secret_hash = x(acceptance['secret_hash'])

  peer_refund_tx = CTransaction.deserialize(x(acceptance['tx2']))
  own_send_tx = audit.load_tx('3_tx3.txt')
  own_refund_tx = CMutableTransaction.from_tx(CTransaction.deserialize(x(confirmation['tx4'])))

  # Apply signatures to TX4 and check the result is valid
  txin_scriptPubKey = own_send_tx.vout[0].scriptPubKey
  sighash = SignatureHash(txin_scriptPubKey, own_refund_tx, 0, SIGHASH_ALL)
  own_refund_tx_sig_a = x(send_notification['tx4_sig'])
  if not public_key_a.verify(sighash, own_refund_tx_sig_a):
    raise TradeError("Own signature for recovery transaction is invalid.")
  own_refund_tx_sig_b = get_recovery_tx_sig(own_refund_tx, private_key_b, public_key_b, public_key_a, secret_hash)
  if not public_key_b.verify(sighash, own_refund_tx_sig_b):
    raise TradeError("Signature from peer for TX4 is invalid.")

  own_refund_tx.vin[0].scriptSig = build_recovery_in_script(own_refund_tx_sig_a, public_key_a, own_refund_tx_sig_b, public_key_b)
  bitcoin.core.scripteval.VerifyScript(own_refund_tx.vin[0].scriptSig, txin_scriptPubKey, own_refund_tx, 0, (SCRIPT_VERIFY_P2SH,))
  audit.save_tx('5_tx4.txt', own_refund_tx)

  # Check TX1 has been confirmed
  altcoin.SelectParams(offer['ask_currency_hash'])
  proxy = altcoin.rpc.AltcoinProxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])
  statbuf = os.stat(audit.get_path('3_tx3.txt'))
  print "Waiting for TX " + b2lx(peer_refund_tx.vin[0].prevout.hash) + " to confirm"
  peer_send_tx = wait_for_tx_to_confirm(proxy, audit, peer_refund_tx.vin[0].prevout.hash, statbuf.st_mtime)
  # TODO: Should have the option to wait for multiple confirmations
  assert_spend_tx_valid(peer_send_tx, int(offer['ask_currency_quantity']), public_key_a, public_key_b, secret_hash)

  # Relay our own send transaction
  altcoin.SelectParams(offer['offer_currency_hash'])
  proxy = altcoin.rpc.AltcoinProxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])
  proxy.sendrawtransaction(own_send_tx)

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
