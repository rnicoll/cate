import praw
from bitcoin.base58 import decode
from decimal import Decimal
import json
import os.path
import re
import sys
import yaml

from bitcoin.core import *
from bitcoin.core.script import *
from bitcoin.wallet import CBitcoinSecret

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

USER_AGENT = 'CATE - Cross-chain Atomic Trading Engine v0.1'

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

class TradeDao(object):
  """
  Utility class for managing the audit trail of trades in progress. Handles loading/saving,
  as well as ensuring files are not overwritten/that they exist before loading.
  """
  def __init__(self, trade_id):
    if not re.match('^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', trade_id):
      raise ValueException("Trade ID is not a UUID, rejecting for security reasons")

    self.trade_id = trade_id
    self.audit_directory = 'audits' + os.path.sep + trade_id
    if not os.path.isdir('audits'):
      os.mkdir('audits')

    # Log the incoming offer
    if not os.path.isdir(self.audit_directory):
      os.mkdir(self.audit_directory)

  def assert_file_does_not_exist(self, real_path):
    if os.path.isfile(real_path):
      raise error.AuditError("Audit file \"" + real_path + "\" already exists.")

  def assert_file_exists(self, real_path):
    if not os.path.isfile(real_path):
      raise error.AuditError("Audit file \"" + real_path + "\" does not exist.")

  def file_exists(self, filename):
    return os.path.isfile(self.get_path(filename))
  
  def get_path(self, filename):
    return os.path.realpath(self.audit_directory + os.path.sep + filename)

  def load_json(self, filename):
    real_path = self.get_path(filename)
    self.assert_file_exists(real_path)
    with open(real_path, 'r') as json_file:
      return json.load(json_file)

  def load_private_key(self, filename):
    real_path = self.get_path(filename)
    self.assert_file_exists(real_path)
    with open(real_path, 'r') as private_key_file:
      return CBitcoinSecret.from_secret_bytes(x(private_key_file.read()), True)

  def load_secret(self, filename):
    real_path = self.get_path(filename)
    self.assert_file_exists(real_path)
    with open(real_path, 'r', 0700) as secret_file:
      return x(secret_file.read())

  def load_tx(self, filename):
    real_path = self.get_path(filename)
    self.assert_file_exists(real_path)
    with open(real_path, 'r') as tx_file:
      return CTransaction.deserialize(x(tx_file.read()))

  def save_json(self, filename, data):
    real_path = self.get_path(filename)
    self.assert_file_does_not_exist(real_path)
    with open(real_path, 'w') as json_file:
      json.dump(data, json_file, indent=4, separators=(',', ': '))

  def save_private_key(self, filename, secret_bytes):
    real_path = self.get_path(filename)
    self.assert_file_does_not_exist(real_path)
    with open(real_path, 'w') as private_key_file:
      private_key_file.write(b2x(secret_bytes))

  def save_tx(self, filename, tx):
    real_path = self.get_path(filename)
    self.assert_file_does_not_exist(real_path)
    with open(real_path, 'w') as tx_file:
      tx_file.write(b2x(tx.serialize()))

  def save_secret(self, filename, secret):
    real_path = self.get_path(filename)
    self.assert_file_does_not_exist(real_path)
    with open(real_path, "w", 0700) as secret_file:
      secret_file.write(b2x(secret))

  def save_text(self, filename, text):
    real_path = self.get_path(filename)
    self.assert_file_does_not_exist(real_path)
    with open(real_path, "w", 0700) as text_file:
      text_file.write(text)
