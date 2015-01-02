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
    raise Exception('Insufficient funds.')

  return (txins, total_in)

def build_tx1(proxy, quantity, own_address, peer_address, secret, fee_rate):
  """
  Generates "TX1" from the guide at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading
  Pay w BTC to <B's public key> if (x for H(x) known and signed by B) or (signed by A & B)

  proxy is the RPC proxy to the relevant daemon JSON-RPC interface
  quantity is the number of coins to be sent, expressed as an integer quantity of the smallest
    unit (i.e. Satoshi)
  peer_public_key is the public key with which the payment and refund transactions must be signed, expressed as CBase58Data
  own_public_key is the public key this client signs the refund transaction with, expressed as CBase58Data
  secret the secret value to be revealed for the payment transaction

  returns a CTransaction
  """
  # TODO: Use actual transaction size once we have a good estimate
  quantity_inc_fee = quantity + fee_rate.get_fee(2000)
  (txins, total_in) = find_inputs(proxy, quantity_inc_fee)

  # scriptSig is either:
  #     0 <signature B> <signature A> 2 <A public key> <B public key> 2
  # or
  #     <shared secret> <signature B> <B public key> 0
  tx_script = CScript(
    [
      OP_IF,
        OP_2DUP, # Multisig
          OP_HASH160, peer_address, OP_EQUALVERIFY,
          OP_HASH160, own_address, OP_EQUALVERIFY,
          2, OP_CHECKMULTISIG,
      OP_ELSE,
        OP_DUP, # Single sig + hash
          OP_HASH160, peer_address, OP_EQUALVERIFY,
          OP_CHECKSIGVERIFY,
          OP_HASH256, Hash(secret), OP_EQUAL,
      OP_ENDIF
    ]
  )
  txout = CTxOut(quantity, tx_script)
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
      raise Exception('Transaction came back without all inputs signed.')

  # TODO: Lock outputs which are used by this transaction

  return tx_signed['tx']

def build_tx2(proxy, tx1, nLockTime, own_address, fee_rate):
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

  seckey = proxy.dumpprivkey(own_address)

  txin_scriptPubKey = prev_out.scriptPubKey

  fee = fee_rate.get_fee(1000)
  txouts = [CTxOut(prev_out.nValue - fee, own_address.to_scriptPubKey())]

  # Create the unsigned transaction
  tx = CMutableTransaction([txin], txouts, nLockTime)

  # Calculate the signature hash for the transaction.
  sighash = SignatureHash(txin_scriptPubKey, tx, 0, SIGHASH_ALL)

  # Now sign it. We have to append the type of signature we want to the end, in
  # this case the usual SIGHASH_ALL.
  sig = seckey.sign(sighash) + bytes([SIGHASH_ALL])

  # scriptSig needs to be:
  #     0 <signature B> <signature A> 2 <A public key> <B public key> 2
  # However, we only can do one side, so we leave the rest to the other side to
  # complete
  txin.scriptSig = CScript([sig, seckey.pub])

  return tx

"""
Generate the spending transaction for TX3/TX1.

seckey is the secret key used to sign the transaction
"""
def build_tx3_spend(proxy, tx1, secret, own_address):
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
  sig = seckey.sign(sighash) + bytes([SIGHASH_ALL])

  # scriptSig needs to be:
  #     <shared secret> <signature B> <B public key> 0
  txin.scriptSig = CScript([secret, sig, seckey.pub, 0])

  return tx
