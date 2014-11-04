import praw
from bitcoin.base58 import decode
import hashlib
import json
import os.path
from StringIO import StringIO
import yaml
import sys
import uuid

from cate import *

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

# Query the user for details of the transaction
target_redditor = None
offer_currency_code = ''
offer_currency_quantity = 0
ask_currency_code = ''
ask_currency_quantity = 0
receive_address = None

print "Okay, first of all I need to know who to make an offer to."
while target_redditor == None:
  target_username = raw_input("reddit username: ")
  try:
    target_redditor = r.get_redditor(target_username)
  except requests.exceptions.HTTPError:
    print ("Could not find that redditor")
    target_redditor = None

offer_currency_code = input_currency_code("What currency are you offering, and how much?")

while offer_currency_quantity < 1:
  offer_currency_quantity = raw_input("Quantity offered: ")

ask_currency_code = input_currency_code("What currency are you wanting, and how much?")
    
while ask_currency_quantity < 1:
  ask_currency_quantity = raw_input("Quantity asked: ")

print "Finally, where should the coins be sent?"
while not receive_address:
  receive_address = raw_input("Address to send " + ask_currency_code + " to: ")
  if not validate_address(receive_address.strip()):
    receive_address = None
    print "Address is invalid, please try again"

# TODO: Repeat the trade back to the user for them to validate

trade = {
  'trade_id': uuid.uuid4().urn[9:],
  'offer_currency_hash': NETWORK_HASHES[offer_currency_code],
  'offer_currency_quantity': offer_currency_quantity,
  'ask_address': receive_address.strip(),
  'ask_currency_hash': NETWORK_HASHES[ask_currency_code],
  'ask_currency_quantity': ask_currency_quantity
}

io = StringIO()
json.dump(trade, io)
trade_json = io.getvalue()

r.send_message(target_redditor, 'CATE transaction offer', trade_json)

print "Offer sent"

