import praw
from bitcoin.base58 import decode
from decimal import Decimal
import hashlib
import json
import locale
import os.path
from StringIO import StringIO
import sys
import re

from bitcoin import SelectParams
from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.rpc
from cate import *

def assert_offer_valid(offer):
  """
  Validates the given offer, and throws an Exception in case of problems.
  """

  if 'trade_id' not in offer:
    raise Exception( "Missing trade ID from received offer.")

  if 'ask_currency_hash' not in offer \
    or 'ask_currency_quantity' not in offer \
    or 'ask_address' not in offer:
    raise Exception( "Missing details of currency being asked for from received offer.")

  if 'offer_currency_hash' not in offer \
    or 'offer_currency_quantity' not in offer:
    raise Exception( "Missing details of offered currency from received offer.")

  if offer['offer_currency_hash'] not in NETWORK_CODES:
    raise Exception( "Offered currency genesis hash " + offer['offer_currency_hash'] + " is unknown.")

  if offer['ask_currency_hash'] not in NETWORK_CODES:
    raise Exception( "Asked currency genesis hash " + offer['ask_currency_hash'] + " is unknown.")

  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]

  if not offer['offer_currency_quantity'].isnumeric():
    raise Exception( "Offered currency quantity is not a number.")
      
  if not offer['ask_currency_quantity'].isnumeric():
    raise Exception( "Asked currency quantity is not a number.")
      
  offer_currency_quantity = Decimal(offer['offer_currency_quantity'])
  ask_currency_quantity = Decimal(offer['ask_currency_quantity'])

  if offer_currency_quantity < 0.00000001:
    raise Exception( "Offered currency quantity is below minimum trade value.")

  if ask_currency_quantity < 0.00000001:
    raise Exception( "Asked currency quantity is below minimum trade value.")

  # TODO: Ensure there's no data past the 8th digit in the quantity

  if not validate_address(offer['ask_address']):
    raise Exception( "Address to send coins to is invalid.")

  if not re.search('^[\w\d][\w\d-]+[\w\d]$', offer['trade_id']):
    raise Exception( "Trade ID is invalid, expected a UUID.")

  return

def process_offer(offer_json):
  """
  """

  offer = json.loads(offer_json)
  assert_offer_valid(offer)

  trade_id = offer['trade_id']
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  offer_currency_quantity = Decimal(offer['offer_currency_quantity'])
  ask_currency_quantity = Decimal(offer['ask_currency_quantity'])
  ask_address = offer['ask_address']

  # We validate the trade ID already, but double check here
  if trade_id.find(os.path.sep) != -1:
    raise Exception("Invalid trade ID received; trade must not contain path separators")

  # Log the incoming offer
  audit_directory = 'audits' + os.path.sep + trade_id
  if not os.path.isdir(audit_directory):
    os.mkdir(audit_directory)

  audit_filename = audit_directory + os.path.sep + 'received_offer.json'
  if os.path.isfile(audit_filename):
    print "Offer " + trade_id + " already received, ignoring offer"
    return False
  else:
    with open(audit_filename, "w") as audit_file:
      io = StringIO()
      json.dump(offer, io, indent=4, separators=(',', ': '))
      audit_file.write(io.getvalue())

  # TODO: Clean up this mess
  # TODO: Include details of who offered the trade
  print "Received offer " + trade_id + " of " + locale.format("%.8f", offer_currency_quantity, True) + " " + offer_currency_code + " for " \
    + locale.format("%.8f", ask_currency_quantity, True) + " " + ask_currency_code

  # TODO: Prompt the user for whether the trade is acceptable
  # TODO: If the trade is not acceptable, stop (send rejection notice?)
  # TODO: If the trade is acceptable:
  #	Generate a very large secret number (i.e. around 128 bits)
  secret = os.urandom(16)
  with open(audit_directory + os.path.sep + 'secret.txt', "w", 0700) as secret_file:
    secret_file.write(secret)

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  # TODO: Generate a key for us and write it to disk
  #own_private_key = None # https://github.com/weex/addrgen/blob/master/addrgen.py - see generate()
  #own_public_key = None
  # TODO Get key for the peer from the trade request
  #peer_public_key = None

  #	Generate TX1 & TX2 as per https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
  #tx1 = build_tx1(proxy, ask_currency_quantity, own_public_key, peer_public_key, secret)
  #tx2 = build_tx2(proxy, tx1.vout[0], ask_currency_quantity, own_private_key)
  #     Write TX1 and TX2 to directory
  #with open(audit_directory + os.path.sep + 'tx2.txt', "w") as tx1_file:
  #  tx1_file.write(tx1.serialize())
  #with open(audit_directory + os.path.sep + 'tx2.txt', "w") as tx2_file:
  #  tx2_file.write(tx2.serialize())

  #	Send TX2 to remote user along with our public key


  #	Await signed TX2 and TX4 returned from remote user (another script to handle this)

  return True

# TODO: Should use a more specific exception
try:
  config = load_configuration("config.yml")
except Exception as e:
  print e
  sys.exit(0)

r = praw.Reddit(user_agent = "CATE - Cross-chain Atomic Trading Engine")
# TODO: Should use a more specific exception
try:
  reddit_login(r, config)
except Exception as e:
  print e
  sys.exit(0)

if not os.path.isdir('audits'):
  os.mkdir('audits')

for message in r.get_messages():
  if message.subject == "CATE transaction offer":
    try:
      process_offer(message.body)
    # TODO: Should use a more specific exception
    except Exception as e:
      print e
      sys.exit(0)
