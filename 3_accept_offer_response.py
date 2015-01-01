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

for message in r.get_messages():
  if message.subject == "CATE transaction accepted":
    try:
      process_offer_response(message.author, message.body)
    # TODO: Should use a more specific exception
    except Exception as e:
      print e
      sys.exit(0)
