import praw
import calendar
import datetime
from decimal import Decimal
import json
import locale
import os.path
import socket
import sys
import re

import altcoin
import altcoin.rpc
from altcoin.core.key import CAltcoinECKey

from cate import *
from cate.error import AuditError, ConfigurationError, MessageError
from cate.fees import CFeeRate
from cate.tx import *

def assert_offer_valid(offer):
  """
  Validates the given offer, and throws an Exception in case of problems.
  """

  if 'trade_id' not in offer:
    raise MessageError( "Missing trade ID from received offer.")
  if offer['trade_id'].find(os.path.sep) != -1:
    raise MessageError( "Invalid trade ID received; trade must not contain path separators")

  if 'ask_currency_hash' not in offer \
    or 'ask_currency_quantity' not in offer:
    raise MessageError( "Missing details of currency being asked for from received offer.")

  if 'public_key_b' not in offer:
    raise MessageError( "Missing peer's public key from received offer." )

  if 'offer_currency_hash' not in offer \
    or 'offer_currency_quantity' not in offer:
    raise MessageError( "Missing details of offered currency from received offer.")

  if offer['offer_currency_hash'] not in NETWORK_CODES:
    raise MessageError( "Offered currency genesis hash " + offer['offer_currency_hash'] + " is unknown.")

  if offer['ask_currency_hash'] not in NETWORK_CODES:
    raise MessageError( "Asked currency genesis hash " + offer['ask_currency_hash'] + " is unknown.")

  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]

  if not isinstance( offer['offer_currency_quantity'], ( int, long ) ):
    raise MessageError( "Offered currency quantity is not a number.")

  if not isinstance( offer['ask_currency_quantity'], ( int, long ) ):
    raise MessageError( "Asked currency quantity is not a number.")

  if offer_currency_code not in config['daemons']:
    raise MessageError( "Offered currency " + offer_currency_code + " is not configured.")
  if ask_currency_code not in config['daemons']:
    raise MessageError( "Asked currency " + ask_currency_code + " is not configured.")

  offer_currency_quantity = offer['offer_currency_quantity']
  ask_currency_quantity = offer['ask_currency_quantity']

  if offer_currency_quantity < 1:
    raise MessageError( "Offered currency quantity is below minimum trade value.")

  if ask_currency_quantity < 1:
    raise MessageError( "Asked currency quantity is below minimum trade value.")

  # TODO: Try to validate the public key as much as possible

  if not re.search('^[\w\d][\w\d-]+[\w\d]$', offer['trade_id']):
    raise MessageError( "Trade ID is invalid, expected a UUID.")

  return

def process_offer(offer, audit):
  """
  Parses and validates an offer from step 1, and if the user agrees to the offer,
  generates the "send" and "refund" transactions.
  """

  trade_id = offer['trade_id']
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  offer_currency_quantity = offer['offer_currency_quantity']
  ask_currency_quantity = offer['ask_currency_quantity']
  public_key_b = bitcoin.core.key.CPubKey(x(offer['public_key_b']))

  # TODO: Include details of who offered the trade
  print "Received offer " + trade_id + " of " \
    + locale.format("%.8f", Decimal(offer_currency_quantity) / COIN, True) + " " + offer_currency_code + " for " \
    + locale.format("%.8f", Decimal(ask_currency_quantity) / COIN, True) + " " + ask_currency_code

  answer = raw_input("\n\nDo you want to continue with the trade? 'Y' to continue, 'N' to quit.  ").lower()
  while answer not in ['y', 'yes', 'n', 'no']:
    answer = raw_input("Not recognised. 'Y' to continue, 'N' to quit.  ").lower()
  if answer in ['n', 'no']:
    print "\nTrade aborted"
    exit(0)

  # TODO: If the trade is not acceptable, stop (send rejection notice?)
  # TODO: If the trade is acceptable, continue

  # Connect to the daemon
  # TODO: Check the configuration exists
  altcoin.SelectParams(offer['ask_currency_hash'])
  proxy = altcoin.rpc.AltcoinProxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  fee_rate = CFeeRate(config['daemons'][ask_currency_code]['fee_per_kb'])
  
  #	Generate a very large secret number (i.e. around 128 bits)
  secret = os.urandom(16)
  secret_hash = bitcoin.core.Hash(secret)
  audit.save_secret('2_secret.txt', secret)

  # Generate a key pair to be used to sign transactions. We generate the key
  # directly rather than via a wallet as it's used on both chains.
  cec_key = CAltcoinECKey()
  cec_key.generate()
  cec_key.set_compressed(True)
  audit.save_private_key('2_private_key.txt', cec_key.get_secretbytes())
  public_key_a = cec_key.get_pubkey()
  private_key_a = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(cec_key.get_secretbytes(), True)

  #	Generate TX1 & TX2 as per https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
  lock_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=48)
  nLockTime = calendar.timegm(lock_datetime.timetuple())
  own_address = proxy.getnewaddress("CATE refund " + trade_id)
  send_tx = build_send_transaction(proxy, ask_currency_quantity, public_key_a, public_key_b, secret_hash, fee_rate)
  send_tx_n = 0
  refund_tx = build_unsigned_refund_tx(proxy, send_tx, send_tx_n, own_address, nLockTime, fee_rate)

  #     Write TX1 to the audit directory as it's not sent to the peer
  audit.save_tx('2_tx1.txt', send_tx)

  #	Send TX2 to remote user along with our address
  return {
    'trade_id': trade_id,
    'secret_hash': b2x(secret_hash),
    'public_key_a': b2x(public_key_a),
    'tx2': b2x(refund_tx.serialize())
  }

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(1)

r = praw.Reddit(user_agent = USER_AGENT)
try:
  reddit_login(r, config)
except ConfigurationError as e:
  print e
  sys.exit(1)

if not os.path.isdir('audits'):
  os.mkdir('audits')

for message in r.get_messages():
  if message.subject != "CATE transaction offer (1)":
    continue
  offer = json.loads(message.body)
  try:
    assert_offer_valid(offer)
  except MessageError as err:
    print("Received invalid trade from " + message.author.name)
    continue

  trade_id = offer['trade_id']
  audit = TradeDao(trade_id)
  if audit.file_exists('2_offer.json'):
    print "Offer " + trade_id + " already received, ignoring offer"
    continue
  audit.save_json('2_offer.json', offer)

  try:
    response = process_offer(offer, audit)
  except socket.error as err:
    print "Could not connect to wallet."
    sys.exit(1)
  if not response:
    break

  audit.save_json('2_acceptance.json', response)

  r.send_message(message.author, 'CATE transaction accepted (2)', json.dumps(response))
