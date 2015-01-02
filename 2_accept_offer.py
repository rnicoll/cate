import praw
import calendar
import datetime
from decimal import Decimal
import json
import locale
import os.path
import socket
from StringIO import StringIO
import sys
import re

from bitcoin import SelectParams
import bitcoin.rpc
from cate import *
from cate.fees import CFeeRate
from cate.tx import *
from cate.fees import CFeeRate

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

  if 'b_public_key' not in offer:
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

def process_offer(offer, audit_directory):
  """
  Parses and validates an offer from step 1, and if the user agrees to the offer,
  generates the "send" and "refund" transactions.
  """

  trade_id = offer['trade_id']
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  offer_currency_quantity = offer['offer_currency_quantity']
  ask_currency_quantity = offer['ask_currency_quantity']
  public_key_b = bitcoin.core.key.CPubKey(x(offer['b_public_key']))

  # TODO: Include details of who offered the trade
  print "Received offer " + trade_id + " of " \
    + locale.format("%.8f", Decimal(offer_currency_quantity) / COIN, True) + " " + offer_currency_code + " for " \
    + locale.format("%.8f", Decimal(ask_currency_quantity) / COIN, True) + " " + ask_currency_code

  # TODO: Prompt the user for whether the trade is acceptable

  # TODO: If the trade is not acceptable, stop (send rejection notice?)
  # TODO: If the trade is acceptable, continue

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  fee_rate = CFeeRate(config['daemons'][ask_currency_code]['fee_per_kb'])
  
  #	Generate a very large secret number (i.e. around 128 bits)
  secret = os.urandom(16)
  secret_hash = Hash(secret)
  #     Write secret to the audit directory as it's not sent to the peer
  with open(audit_directory + os.path.sep + '2_secret.txt', "w", 0700) as secret_file:
    secret_file.write(secret)

  # Generate a key pair to be used to sign transactions. We generate the key
  # directly rather than via a wallet as it's used on both chains.
  cec_key = bitcoin.core.key.CECKey()
  cec_key.generate()
  cec_key.set_compressed(True)
  with open(audit_directory + os.path.sep + '2_secret.txt', "w", 0700) as secret_file:
    secret_file.write(b2x(cec_key.get_secretbytes()))
  public_key_a = cec_key.get_pubkey()
  private_key_a = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(cec_key.get_secretbytes(), True)

  #	Generate TX1 & TX2 as per https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
  lock_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=48)
  lock_time = calendar.timegm(lock_datetime.timetuple())
  own_address = proxy.getnewaddress("CATE refund " + trade_id)
  tx1 = build_tx1_tx3(proxy, ask_currency_quantity, public_key_a, public_key_b, secret_hash, fee_rate)
  tx2 = build_unsigned_tx2_tx4(proxy, tx1, own_address, lock_time, fee_rate)

  #     Write TX1 to the audit directory as it's not sent to the peer
  with open(audit_directory + os.path.sep + '2_tx1.txt', "w") as tx1_file:
    tx1_file.write(b2x(tx1.serialize()))

  #	Send TX2 to remote user along with our address
  return {
    'trade_id': trade_id,
    'secret_hash': b2x(Hash(secret)),
    'a_public_key': b2x(public_key_a),
    'tx2': b2x(tx2.serialize())
  }

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(1)

r = praw.Reddit(user_agent = "CATE - Cross-chain Atomic Trading Engine")
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
  assert_offer_valid(offer)
  trade_id = offer['trade_id']
  audit_directory = ensure_audit_directory_exists(trade_id)

  # Log the incoming offer
  audit_directory = ensure_audit_directory_exists(trade_id)
  audit_filename = audit_directory + os.path.sep + '2_offer.json'
  if os.path.isfile(audit_filename):
    print "Offer " + trade_id + " already received, ignoring offer"
    continue

  with open(audit_filename, "w") as audit_file:
    io = StringIO()
    json.dump(offer, io, indent=4, separators=(',', ': '))
    audit_file.write(io.getvalue())

  try:
    response = process_offer(offer, audit_directory)
  except socket.error as err:
    print "Could not connect to wallet."
    sys.exit(1)

  if not response:
    break

  io = StringIO()
  json.dump(response, io)

  # Record the message
  with open(audit_directory + os.path.sep + '2_acceptance.json', "w", 0700) as response_file:
    response_file.write(io.getvalue())

  r.send_message(message.author, 'CATE transaction accepted (2)', io.getvalue())
