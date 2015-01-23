import praw
import json
import os.path
import yaml
import sys
import uuid

from altcoin.core.key import CAltcoinECKey

from cate import *
from cate.error import ConfigurationError

def input_trade(trade_id, pubkey):
  offer_currency_code = ''
  offer_currency_quantity = 0
  ask_currency_code = ''
  ask_currency_quantity = 0

  while offer_currency_code not in NETWORK_HASHES:
    offer_currency_code = input_currency_code("What currency are you offering, and how much?")
    # TODO: Show a useful error on invalid code

  while offer_currency_quantity < Decimal(0.00000001):
    offer_currency_quantity = Decimal(raw_input("Quantity offered: "))

  while ask_currency_code not in NETWORK_HASHES:
    ask_currency_code = input_currency_code("What currency are you wanting, and how much?")
    # TODO: Show a useful error on invalid code
      
  while ask_currency_quantity < Decimal(0.00000001):
    ask_currency_quantity = Decimal(raw_input("Quantity asked: "))

  # TODO: Repeat the trade back to the user for them to validate

  return {
    'trade_id': trade_id,
    'offer_currency_hash': NETWORK_HASHES[offer_currency_code],
    'offer_currency_quantity': int(offer_currency_quantity * COIN),
    'public_key_b': b2x(pubkey),
    'ask_currency_hash': NETWORK_HASHES[ask_currency_code],
    'ask_currency_quantity': int(ask_currency_quantity * COIN)
  }

def input_currency_code(prompt):
  """
  Prompts the user for a currency code (BTC, LTC, DOGE, etc.) and repeats the prompt until
  a valid input is presented.
  """
  currency_code = None
  print prompt
  while currency_code not in NETWORK_HASHES:
    currency_code = raw_input("Currency (BTC, LTC, DOGE): ")
    if currency_code.upper() not in NETWORK_HASHES:
      print ("Unknown currency \"" + currency_code + "\", please enter one of: ") + ", ".join(NETWORK_HASHES.keys())
    else:
      currency_code = currency_code.upper()

  return currency_code

try:
  config = load_configuration("config.yml")
except Exception as e:
  print e
  sys.exit(1)

r = praw.Reddit(user_agent = USER_AGENT)
try:
  reddit_login(r, config)
except ConfigurationError as e:
  print e
  sys.exit(1)

# Create a unique trade ID
trade_id = uuid.uuid1().urn[9:]
audit = TradeDao(trade_id)

# Query the user for details of the transaction
target_redditor = None

print "Okay, first of all I need to know who to make an offer to."
while target_redditor == None:
  target_username = raw_input("reddit username: ")
  try:
    target_redditor = r.get_redditor(target_username)
  except requests.exceptions.HTTPError:
    print ("Could not find that redditor")
    target_redditor = None

# Generate a key pair to be used to sign transactions. We generate the key
# directly rather than via a wallet as it's used on both chains.
cec_key = CAltcoinECKey()
cec_key.generate()
cec_key.set_compressed(True)
audit.save_private_key('1_private_key.txt', cec_key.get_secret_bytes())

trade = input_trade(trade_id, cec_key.get_pubkey())
audit.save_json('1_offer.json', trade)

r.send_message(target_redditor, 'CATE transaction offer (1)', json.dumps(trade))

print "Trade offer " + trade_id + " sent"
