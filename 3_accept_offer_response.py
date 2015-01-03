import praw
from bitcoin.base58 import decode
import calendar
import datetime
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

def process_offer_accepted(acceptance, audit_directory):
  trade_id = acceptance['trade_id']
  secret_hash = x(acceptance['secret_hash'])
  tx2 = CTransaction.deserialize(x(acceptance['tx2']))

  # Load the offer sent
  with open(audit_directory + os.path.sep + '1_offer.json', "r") as offer_file:
    offer = json.loads(offer_file.read())

  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  ask_currency_quantity = offer['ask_currency_quantity']
  offer_currency_quantity = offer['offer_currency_quantity']

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])
  fee_rate = CFeeRate(config['daemons'][offer_currency_code]['fee_per_kb'])

  public_key_a = bitcoin.core.key.CPubKey(x(acceptance['public_key_a']))
  with open(audit_directory + os.path.sep + '1_private_key.txt', "r") as private_key_file:
    private_key_b = bitcoin.wallet.CBitcoinSecret.from_secret_bytes(x(private_key_file.read()), True)
  public_key_b = bitcoin.core.key.CPubKey(x(offer['public_key_b']))

  assert_tx2_valid(tx2)
  tx2_sig = get_tx2_tx4_signature(proxy, tx2, private_key_b, public_key_a, public_key_b, secret_hash)

  # Generate TX3 & TX4, which are essentially the same as TX1 & TX2 except
  # that ask/offer details are reversed
  lock_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=48)
  lock_time = calendar.timegm(lock_datetime.timetuple())
  own_address = proxy.getnewaddress("CATE refund " + trade_id)
  tx3 = build_tx1_tx3(proxy, offer_currency_quantity, public_key_a, public_key_b, secret_hash, fee_rate)
  tx4 = build_unsigned_tx2_tx4(proxy, tx3, own_address, lock_time, fee_rate)

  #     Write TX3 to audit directory as we don't send it yet
  with open(audit_directory + os.path.sep + '3_tx3.txt', "w") as tx3_file:
    tx3_file.write(b2x(tx3.serialize()))

  return {
    'trade_id': trade_id,
    'tx2_sig': b2x(tx2_sig),
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
  if message.subject != "CATE transaction accepted (2)":
    continue
  acceptance = json.loads(message.body)
  try:
    assert_acceptance_valid(acceptance)
  except MessageError as err:
    print("Received invalid trade from " + message.author.name)
    continue

  trade_id = acceptance['trade_id']
  audit_directory = ensure_audit_directory_exists(trade_id)

  audit_filename = audit_directory + os.path.sep + '3_acceptance.json'
  if os.path.isfile(audit_filename):
    print "Offer acceptance " + trade_id + " already received, ignoring offer"
    continue

  # Record the received response
  with open(audit_directory + os.path.sep + '3_acceptance.json', "w", 0700) as response_file:
    io = StringIO()
    json.dump(acceptance, io, indent=4, separators=(',', ': '))
    response_file.write(io.getvalue())

  response = process_offer_accepted(acceptance, audit_directory)
  if not response:
    break

  io = StringIO()
  json.dump(response, io)

  # Record the message
  with open(audit_directory + os.path.sep + '3_confirmation.json', "w", 0700) as response_file:
    response_file.write(io.getvalue())

  r.send_message(message.author, 'CATE transaction confirmed (3)', io.getvalue())
