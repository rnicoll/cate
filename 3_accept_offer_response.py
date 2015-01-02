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

# 'B':
# 1. Receives the partial TX2 and hash value for the secret from 'A'.
# 2. Verifies lock time on TX2 is valid
# 3. Signs TX2 to complete it
# 4. Generates TX3 and signs it
# 5. Generates TX4 and signs it
# 4. Returns the partial TX4 to 'A'
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
      process_offer_accepted(message.author, message.body)
    # TODO: Should use a more specific exception
    except Exception as e:
      print e
      sys.exit(0)
