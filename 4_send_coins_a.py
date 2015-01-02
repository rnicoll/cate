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
import bitcoin.rpc
from cate import *
from cate.error import *

def assert_confirmation_valid(confirmation):
  if 'trade_id' not in confirmation:
    raise MessageError( "Missing trade ID from confirmed offer.")
  if offer['trade_id'].find(os.path.sep) != -1:
    raise MessageError("Invalid trade ID received; trade must not contain path separators")
  if 'tx2' not in confirmation:
    raise MessageError( "Missing TX2 refund transaction from confirmed offer.")
  if 'tx4' not in confirmation:
    raise MessageError( "Missing TX4 refund transaction from confirmed offer.")

def process_offer_confirmed(redditor, confirmation):
  """
  1. Receives the completed TX2 and the partial TX4 from 'B'.
  2. Verifies the signatures on TX2 are valid
  3. Verifies the lock time on TX4 is valid
  4. Signs TX4 to complete it
  5. Returns TX4 to 'B'
  6. Submits TX1 to the network
  """

  audit_directory = ensure_audit_directory_exists(trade_id)

  # Record the received response
  with open(audit_directory + os.path.sep + 'offer_confirmation_received.json', "w", 0700) as response_file:
    io = StringIO()
    json.dump(confirmation, io, indent=4, separators=(',', ': '))
    response_file.write(io.getvalue())

  with open(audit_directory + os.path.sep + 'received_offer.json', "r") as offer_file:
    offer_json = offer_file.read()
    offer = json.loads(offer_json)

  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  ask_currency_quantity = offer['ask_currency_quantity']
  offer_currency_quantity = offer['offer_currency_quantity']
  ask_address = offer['ask_address']

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  # FIXME: Pull signatures from the returned TX2, apply them to our copy
  # of TX2, and then verify they complete the signature

  assert_tx2_valid(tx4)
  sign_tx2(proxy, tx4, own_address, peer_address, secret_hash)

  # Load TX1 from disk
  with open(audit_directory + os.path.sep + 'tx1.txt', "r") as tx1_file:
    tx1 = CTransaction(x(tx1_file.read()))

  proxy.sendrawtransaction(tx1)

  response = {
    'trade_id': trade_id,
    'tx4': b2x(tx4.serialize())
  }
  io = StringIO()
  json.dump(response, io)

  # Record the message
  with open(audit_directory + os.path.sep + 'offer_coins_sent.json', "w", 0700) as response_file:
    response_file.write(io.getvalue())

  r.send_message(other_redditor, 'CATE transaction sent (4)', io.getvalue())

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
  if message.subject == 'CATE transaction confirmed (3)':
    confirmation = json.loads(message.body)
    assert_confirmation_valid(offer)
    if not process_offer_confirmed(message.author, confirmation):
      break
