import praw
from bitcoin.base58 import decode
from decimal import Decimal
import hashlib
import os.path
from StringIO import StringIO
import yaml
import sys

from bitcoin.core import *
from bitcoin.core.script import *

ADDRESS_LENGTH = 25

# hashes of the genesis blocks for each network
NETWORK_HASHES = {
  'BTC': '000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943',
  'DOGE': 'bb0a78264637406b6360aad926284d544d7049f45189db5664f3c4d07350559e',
  'LTC': 'f5ae71e26c74beacc88382716aced69cddf3dffff24f384e1808905e0188f68f'
}

# Reverse-map of NETWORK_HASHES
NETWORK_CODES = {
  '000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943': 'BTC',
  'bb0a78264637406b6360aad926284d544d7049f45189db5664f3c4d07350559e': 'DOGE',
  'f5ae71e26c74beacc88382716aced69cddf3dffff24f384e1808905e0188f68f': 'LTC'
}

def ensure_audit_directory_exists(trade_id):
  """
  Generates a directory path within which to store audit details for a trade.
  Returns the path to the directory.
  """
  if not os.path.isdir('audits'):
    os.mkdir('audits')

  # Log the incoming offer
  audit_directory = 'audits' + os.path.sep + trade_id
  if not os.path.isdir(audit_directory):
    os.mkdir(audit_directory)

  return audit_directory

def load_configuration(filename):
  if not os.path.isfile(filename):
    raise error.ConfigurationError("Expected configuration file '" + filename + "'")

  with open(filename, 'r') as f:
    raw_config = f.read(10 * 1024 * 1024) # If you have a config over 10M in size, I give up

  try:
    config = yaml.load(raw_config)
  except yaml.parser.ParserError as e:
    raise error.ConfigurationError("Could not parse configuration file: {0}".format(e))

  return config

def reddit_login(r, config):
  """
  Read the configuration from disk and log in to reddit. Throws Exception
  in case of problems
  """

  # Check the configuration has a reddit section before trying to log in
  if 'reddit' not in config:
    raise error.ConfigurationError("Expected 'reddit' section in configuration file 'config.yml'")

  # Try to log in
  reddit_config = config['reddit']
  if 'username' not in reddit_config or 'password' not in reddit_config:
    raise error.ConfigurationError("Expected reddit username and password to be provided in password file")

  try:
    r.login(reddit_config['username'].strip(), reddit_config['password'].strip())
  except praw.errors.InvalidUserPass:
    raise error.ConfigurationError("Could not log in to reddit with provided username and password")

  return

def validate_address(address):
  """
  Validates a Bitcoin-format pay-to-key address. Takes in the address as a
  string and returns True if the address is valid, False otherwise.
  """
  if len(address) < ADDRESS_LENGTH:
    return False
  else:
    addr = decode(address)
    if not addr:
      return False
    else:
      version = addr[0]
      checksum = addr[-4:] # Get the last 4 characters of the address
      vh160 = addr[:-4] # Version plus hash160 is what is checksummed

      digester = hashlib.sha256()
      digester.update(vh160)
      vh160_hash = digester.digest()

      digester = hashlib.sha256()
      digester.update(vh160_hash)
      vh160_hash_hash = digester.digest()

      if vh160_hash_hash[0:4] != checksum:
        return False
  return True
