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

# 'A':
# 1. Receives the completed TX2 and the partial TX4 from 'B'.
# 2. Verifies the signatures TX2 are valid
# 3. Verifies the lock time on TX4 is valid
# 4. Signs TX4 to complete it
# 5. Returns TX4 to 'B'
# 6. Submits TX1 to the network

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
  if message.subject == "CATE TX2 signed":
    try:
      process_tx2(message.author, message.body)
    # TODO: Should use a more specific exception
    except Exception as e:
      print e
      sys.exit(0)
