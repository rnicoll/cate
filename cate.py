import praw
from bitcoin.base58 import decode
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

# Generates "TX1" from the guide at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
# Pay w BTC to <B's public key> if (x for H(x) known and signed by B) or (signed by A & B)
#
# inputs is an array of inputs to the transaction
# change_address is a standard address to send change to
# quantity is the number of coins to be sent, expressed as a decimal
# peer_public_key is the public key with which the payment and refund transactions must be signed
# own_public_key is the public key this client signs the refund transaction with
# secret the secret value to be revealed for the payment transaction
#
# returns a CTransaction
def build_tx1(inputs, quantity, own_public_key, peer_public_key, secret):
  # scriptSig is either:
  # 0 <signature B> <signature A> 2 <A public key> <B public key> 2
  # <shared secret> <signature B> <B public key> 0
  script = CScript(
    [
      OP_IF,
        OP_2DUP, # Multisig
          OP_HASH160, hash(peer_public_key), OP_EQUALVERIFY,
          OP_HASH160, hash(own_public_key), OP_EQUALVERIFY,
          2, OP_CHECKMULTISIG,
      OP_ELSE,
        OP_DUP, # Single sig + hash
          OP_HASH160, hash(peer_public_key), OP_EQUALVERIFY,
          OP_CHECKSIGVERIFY,
          OP_HASH160, hash(secret), OP_EQUAL,
      OP_ENDIF
    ]
  )

  return

# Generates "TX2" from the guide at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
# Pay w BTC from TX1 to <A's public key>, locked 48 hours in the future, signed by A
#
# inputs is an array of inputs to the transaction
# change_address is a standard address to send change to
# quantity is the number of coins to be sent, expressed as a decimal
# peer_public_key is the public key with which the payment and refund transactions must be signed
# own_public_key is the public key this client signs the refund transaction with
# secret the secret value to be revealed for the payment transaction
#
# returns a CTransaction
def build_tx2(tx1, own_key):
  return

def load_configuration(filename):
  if not os.path.isfile(filename):
    raise Exception("Expected configuration file '" + filename + "'")

  with open(filename, 'r') as f:
    raw_config = f.read(10 * 1024 * 1024) # If you have a config over 10M in size, I give up

  try:
    config = yaml.load(raw_config)
  except yaml.parser.ParserError as e:
    raise Exception("Could not parse configuration file: {0}".format(e))

  return config

def reddit_login(r, config):
  """
  Read the configuration from disk and log in to reddit. Throws Exception
  in case of problems
  """

  # Check the configuration has a reddit section before trying to log in
  if 'reddit' not in config:
    raise Exception("Expected 'reddit' section in configuration file 'config.yml'")

  # Try to log in
  reddit_config = config['reddit']
  if 'username' not in reddit_config or 'password' not in reddit_config:
    raise Exception("Expected reddit username and password to be provided in password file")

  try:
    r.login(reddit_config['username'].strip(), reddit_config['password'].strip())
  except praw.errors.InvalidUserPass:
    raise Exception("Could not log in to reddit with provided username and password")

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
