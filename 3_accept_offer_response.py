import praw
import calendar
import datetime
import json
import os.path
import socket
import sys

from altcoin import SelectParams
import altcoin.rpc
import bitcoin.core.key

from cate import *
from cate.error import ConfigurationError, MessageError, TradeError
from cate.fees import CFeeRate
from cate.tx import *

def assert_acceptance_valid(acceptance):
  if 'trade_id' not in acceptance:
    raise MessageError( "Missing trade ID from accepted offer.")
  if acceptance['trade_id'].find(os.path.sep) != -1:
    raise MessageError("Invalid trade ID received; trade must not contain path separators")
  if 'secret_hash' not in acceptance:
    raise MessageError( "Missing hash of secret value from accepted offer.")
  if 'tx2' not in acceptance:
    raise MessageError( "Missing TX2 refund transaction from accepted offer.")
  if 'public_key_a' not in acceptance:
    raise MessageError( "Missing peer public key from accepted offer.")
  if len(acceptance['secret_hash']) != 64:
    raise MessageError( "Hash of secret is the wrong length.")

def process_offer_accepted(acceptance, audit):
  trade_id = acceptance['trade_id']
  secret_hash = x(acceptance['secret_hash'])
  peer_refund_tx = CTransaction.deserialize(x(acceptance['tx2']))

  # Load the offer sent
  offer = audit.load_json('1_offer.json')
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  offer_currency_quantity = offer['offer_currency_quantity']

  # Connect to the daemon
  # TODO: Check the configuration exists
  altcoin.SelectParams(offer['offer_currency_hash'])
  proxy = altcoin.rpc.AltcoinProxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])
  fee_rate = CFeeRate(config['daemons'][offer_currency_code]['fee_per_kb'])

  public_key_a = bitcoin.core.key.CPubKey(x(acceptance['public_key_a']))
  private_key_b = audit.load_private_key('1_private_key.txt')
  public_key_b = bitcoin.core.key.CPubKey(x(offer['public_key_b']))

  assert_refund_tx_valid(peer_refund_tx, int(offer['ask_currency_quantity']))
  peer_refund_tx_sig = get_refund_tx_sig(peer_refund_tx, private_key_b, public_key_a, public_key_b, secret_hash)

  # Generate TX3 & TX4, which are essentially the same as TX1 & TX2 except
  # that ask/offer details are reversed
  lock_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=48)
  nLockTime = calendar.timegm(lock_datetime.timetuple())
  own_address = proxy.getnewaddress("CATE refund " + trade_id)
  send_tx = build_send_transaction(proxy, offer_currency_quantity, public_key_b, public_key_a, secret_hash, fee_rate)
  send_tx_n = 0
  own_refund_tx = build_unsigned_refund_tx(send_tx, send_tx_n, own_address, nLockTime, fee_rate)

  #     Write TX3 to audit directory as we don't send it yet
  audit.save_tx('3_tx3.txt', send_tx)

  return {
    'trade_id': trade_id,
    'tx2_sig': b2x(peer_refund_tx_sig),
    'tx4': b2x(own_refund_tx.serialize())
  }

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
  if message.subject != "CATE transaction accepted (2)":
    continue
  acceptance = json.loads(message.body)
  try:
    assert_acceptance_valid(acceptance)
  except MessageError as err:
    print("Received invalid trade from " + message.author.name)
    continue

  trade_id = acceptance['trade_id']
  audit = TradeDao(trade_id)
  if audit.file_exists('3_acceptance.json'):
    print "Offer acceptance " + trade_id + " already received, ignoring offer"
    continue
  audit.save_json('3_acceptance.json', acceptance)

  try:
    response = process_offer_accepted(acceptance, audit)
  except socket.error as err:
    print "Could not connect to wallet."
    sys.exit(1)
  if not response:
    break

  audit.save_json('3_confirmation.json', response)

  r.send_message(message.author, 'CATE transaction confirmed (3)', json.dumps(response))
