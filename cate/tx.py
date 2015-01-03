import praw
from bitcoin.base58 import decode
import calendar
import datetime
from decimal import Decimal
import hashlib
import os.path
from StringIO import StringIO
import yaml
import sys

import error

from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.core.serialize

def assert_tx2_valid(tx2):
  """
  Checks the TX2 provided by the remote peer matches the expected structure.
  Raises an exception in case of a problem
  """

  lock_min_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=12)
  lock_max_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=72)
  lock_time = tx2.nLockTime
  if lock_time < calendar.timegm(lock_min_datetime.timetuple()):
    raise error.TradeError("TX2 lock time is "
      + datetime.datetime.utcfromtimestamp(lock_time).strftime('%Y-%m-%d %H:%M:%S')
      + " which is less than 24 hours in the future.")
  if lock_time > calendar.timegm(lock_max_datetime.timetuple()):
    raise error.TradeError("TX2 lock time is "
      + datetime.datetime.utcfromtimestamp(lock_time).strftime('%Y-%m-%d %H:%M:%S')
      + " which is more than 72 hours in the future.")

  if len(tx2.vin) != 1:
    raise error.TradeError("TX2 does not have exactly one input.")
  if len(tx2.vout) != 1:
    raise error.TradeError("TX2 does not exactly one output.")

  # TODO: Check the output value is close to the trade total (i.e. about right
  # after fees have been deducted

  # If the nSequence is 0xffffffff, the transaction can be considered valid
  # despite what the lock time says. Current implementations do not support this,
  # however we want to be sure anyway
  if tx2.vin[0].nSequence == 0xffffffff:
    raise error.TradeError("TX2 input's sequence is final; must be less than MAX_INT.")

def find_inputs(proxy, quantity):
  """ Find unspent outputs equal to or greater than the given target
      quantity.

      quantity is the number of coins to be sent, expressed as an integer quantity of the smallest
      unit (i.e. Satoshi)

      returns a tuple of an array of CMutableTxIn and the total input value
  """

  total_in = 0
  txins = []

  for txout in proxy.listunspent(0):
    total_in += txout['amount']
    txins.append(CMutableTxIn(txout['outpoint']))
    if total_in >= quantity:
      break

  if total_in < quantity:
    raise error.FundsError('Insufficient funds.')

  return (txins, total_in)

def build_tx1_tx3_cscript(public_key_a, public_key_b, secret_hash, is_tx1):
  """
  Generates the script for TX1/TX3's main output (i.e. not the change output)
  """

  if is_tx1:
    single_public_key = public_key_b
  else:
    single_public_key = public_key_a

  # scriptSig is either:
  #     0 <signature B> <signature A> 2 <A public key> <B public key> 2
  # or
  #     <shared secret> <signature B> <B public key> 0
  return CScript(
    [
      OP_IF,
        OP_2DUP, # Multisig
          OP_HASH256, bitcoin.core.Hash(public_key_a), OP_EQUALVERIFY,
          OP_HASH256, bitcoin.core.Hash(public_key_b), OP_EQUALVERIFY,
          2, OP_CHECKMULTISIG,
      OP_ELSE,
        OP_DUP, # Secret and single signature
          OP_HASH256, bitcoin.core.Hash(single_public_key), OP_EQUALVERIFY,
          OP_CHECKSIGVERIFY,
        OP_HASH256, secret_hash, OP_EQUAL,
      OP_ENDIF
    ]
  )

def build_tx1_tx3(proxy, quantity, public_key_a, public_key_b, secret_hash, fee_rate, is_tx1):
  """
  Generates "TX1" from the guide at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
  Pay w BTC to <B's public key> if (x for H(x) known and signed by B) or (signed by A & B)

  proxy is the RPC proxy to the relevant daemon JSON-RPC interface
  quantity is the number of coins to be sent, expressed as an integer quantity of the smallest
    unit (i.e. Satoshi)
  public_key_b is the public key with which the payment and refund transactions must be signed, expressed as CBase58Data
  public_key_a is the public key this client signs the refund transaction with, expressed as CBase58Data
  secret_hash the secret value passed through SHA256 twice

  returns a CTransaction
  """
  # TODO: Use actual transaction size once we have a good estimate
  quantity_inc_fee = quantity + fee_rate.get_fee(2000)
  (txins, total_in) = find_inputs(proxy, quantity_inc_fee)

  txout = CTxOut(quantity, build_tx1_tx3_cscript(public_key_a, public_key_b, secret_hash, is_tx1))
  txouts = [txout]

  # Generate a change transaction if needed
  if total_in > quantity_inc_fee:
    change = total_in - quantity_inc_fee
    change_address = proxy.getrawchangeaddress()
    change_txout = CTxOut(change, change_address.to_scriptPubKey())
    txouts.append(change_txout)

  tx = CMutableTransaction(txins, txouts)
  tx_signed = proxy.signrawtransaction(tx)
  if not tx_signed['complete']:
      raise error.TradeError('Transaction came back without all inputs signed.')

  # TODO: Lock outputs which are used by this transaction

  return tx_signed['tx']

def build_tx1(proxy, quantity, public_key_a, public_key_b, secret_hash, fee_rate):
  return build_tx1_tx3(proxy, quantity, public_key_a, public_key_b, secret_hash, fee_rate, True)
def build_tx3(proxy, quantity, public_key_a, public_key_b, secret_hash, fee_rate):
  return build_tx1_tx3(proxy, quantity, public_key_a, public_key_b, secret_hash, fee_rate, False)

def build_unsigned_tx2_tx4(proxy, tx1, own_address, nLockTime, fee_rate, is_tx2):
  """
  Generates "TX2" from the guide at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading.
  The same code can also generate TX4. These are the refund transactions in case of
  problems. Transaction outputs are locked to the script:
        Pay w BTC from TX1 to <A's public key>, locked 48 hours in the future, signed by A

  proxy is the RPC proxy to the relevant daemon JSON-RPC interface
  tx1 the (complete, signed) transaction to refund from
  nLockTime the lock time to set on the transaction
  own_address is the private address this client signs the refund transaction with. This must match
      the address provided when generating TX1.

  returns a CMutableTransaction
  """
  prev_txid = tx1.GetHash()
  prev_out = tx1.vout[0]
  txin = CMutableTxIn(COutPoint(prev_txid, 0), nSequence=1)

  txin_scriptPubKey = prev_out.scriptPubKey

  fee = fee_rate.get_fee(1000)
  txouts = [CTxOut(prev_out.nValue - fee, own_address.to_scriptPubKey())]

  # Create the unsigned transaction
  return CMutableTransaction([txin], txouts, nLockTime)

def build_unsigned_tx2(proxy, tx1, own_address, nLockTime, fee_rate):
  return build_unsigned_tx2_tx4(proxy, tx1, own_address, nLockTime, fee_rate, True)

def build_unsigned_tx4(proxy, tx3, own_address, nLockTime, fee_rate):
  return build_unsigned_tx2_tx4(proxy, tx3, own_address, nLockTime, fee_rate, False)

"""
Generate the spending transaction for TX1/TX3

seckey is the secret key used to sign the transaction
"""
def build_tx1_tx3_spend(proxy, tx1, private_key, secret, own_address, fee_rate):
  fee = fee_rate.get_fee(1000)

  prev_txid = tx1.GetHash()
  prev_out = tx1.vout[0]
  txin = CMutableTxIn(COutPoint(prev_txid, 0), nSequence=1)
  txins = [txin]

  txin_scriptPubKey = prev_out.scriptPubKey

  txouts = [CTxOut(prev_out.nValue - fee, own_address.to_scriptPubKey())]

  # Create the unsigned transaction
  tx = CMutableTransaction(txins, txouts)

  # Calculate the signature hash for that transaction.
  sighash = SignatureHash(txin_scriptPubKey, tx, 0, SIGHASH_ALL)

  # Now sign it. We have to append the type of signature we want to the end, in
  # this case the usual SIGHASH_ALL.
  sig = private_key.sign(sighash) + (b'\x01') # bytes([SIGHASH_ALL])

  # scriptSig needs to be:
  #     <shared secret> <signature B> <B public key> 0
  txin.scriptSig = CScript([secret, sig, private_key.pub, 0])

  return tx

def get_tx2_tx4_signature(proxy, tx2, own_private_key, public_key_a, public_key_b, secret_hash, is_tx2):
  # Rebuild the input script for TX1
  # Note the inputs are reversed because we're creating it from the peer's point of view
  txin_scriptPubKey = build_tx1_tx3_cscript(public_key_a, public_key_b, secret_hash, is_tx2)

  # Calculate the signature hash for the transaction.
  sighash = SignatureHash(txin_scriptPubKey, tx2, 0, SIGHASH_ALL)

  # Now sign it. We have to append the type of signature we want to the end, in
  # this case the usual SIGHASH_ALL. For some reason appending bytes gives us
  # nonsense, so for now manually stuffing 0x01 on the end
  return own_private_key.sign(sighash) + (b'\x01') # bytes([SIGHASH_ALL])

def get_tx2_signature(proxy, tx2, own_private_key, public_key_a, public_key_b, secret_hash):
  return get_tx2_tx4_signature(proxy, tx2, own_private_key, public_key_a, public_key_b, secret_hash, True)
def get_tx4_signature(proxy, tx2, own_private_key, public_key_a, public_key_b, secret_hash):
  return get_tx2_tx4_signature(proxy, tx2, own_private_key, public_key_a, public_key_b, secret_hash, False)
