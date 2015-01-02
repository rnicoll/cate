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
from cate.error import *
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
  if 'offer_address' not in acceptance:
    raise MessageError( "Missing remote address from accepted offer.")
  if len(acceptance['secret_hash']) != 64:
    raise MessageError( "Hash of secret is the wrong length.")

def process_offer_accepted(redditor, acceptance):
  trade_id = acceptance['trade_id']
  secret_hash = x(acceptance['secret_hash'])
  tx2 = CMutableTransaction.deserialize(x(acceptance['tx2']))

  audit_directory = ensure_audit_directory_exists(trade_id)

  # Record the received response
  with open(audit_directory + os.path.sep + 'offer_acceptance_received.json', "w", 0700) as response_file:
    io = StringIO()
    json.dump(acceptance, io, indent=4, separators=(',', ': '))
    response_file.write(io.getvalue())

  # Load the offer sent
  with open(audit_directory + os.path.sep + 'offer.json', "r") as offer_file:
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

  ask_address = bitcoin.wallet.CBitcoinAddress(offer['ask_address'])
  offer_address = bitcoin.base58.CBase58Data(acceptance['offer_address'])
  own_address = ask_address
  peer_address = offer_address

  assert_tx2_valid(tx2)
  sign_tx2(proxy, tx2, own_address, peer_address, secret_hash)

  # Generate TX3 & TX4, which are essentially the same as TX1 & TX2 except
  # that ask/offer details are reversed
  lock_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=48)
  lock_time = calendar.timegm(lock_datetime.timetuple())
  tx3 = build_tx3(proxy, offer_currency_quantity, own_address, peer_address, secret_hash, fee_rate)
  tx4 = build_tx4(proxy, tx3, lock_time, own_address, fee_rate)

  #     Write TX2-4 to audit directory
  with open(audit_directory + os.path.sep + 'tx2.txt', "w") as tx2_file:
    tx2_file.write(b2x(tx2.serialize()))
  with open(audit_directory + os.path.sep + 'tx3.txt', "w") as tx3_file:
    tx3_file.write(b2x(tx3.serialize()))
  with open(audit_directory + os.path.sep + 'tx4_partial.txt', "w") as tx4_file:
    tx4_file.write(b2x(tx4.serialize()))

  response = {
    'trade_id': trade_id,
    'tx2': b2x(tx2.serialize()),
    'tx4': b2x(tx4.serialize())
  }
  io = StringIO()
  json.dump(response, io)

  # Record the message
  with open(audit_directory + os.path.sep + 'offer_confirmation.json', "w", 0700) as response_file:
    response_file.write(io.getvalue())

  r.send_message(redditor, 'CATE transaction confirmed (3)', io.getvalue())
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
  if message.subject == "CATE transaction accepted":
    acceptance = json.loads(message.body)
    assert_acceptance_valid(acceptance)
    if not process_offer_accepted(message.author, acceptance):
      break
