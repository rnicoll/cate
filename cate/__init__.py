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

def write_secret(audit_directory, secret):
  with open(audit_directory + os.path.sep + '2_secret.txt', "w", 0700) as secret_file:
    secret_file.write(b2x(secret))

def read_secret(audit_directory):
  with open(audit_directory + os.path.sep + '2_secret.txt', "r") as secret_file:
    secret = x(secret_file.read())
  return secret