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

# 'B' receives the completed TX4 from 'A', verifies the signatures on it
# are valid, then submits TX3 to the network

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
  if message.subject == "CATE TX4 signed":
    try:
      process_tx4(message.author, message.body)
    # TODO: Should use a more specific exception
    except Exception as e:
      print e
      sys.exit(0)
