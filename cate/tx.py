import calendar
import datetime
import os.path
import sys

from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.core.serialize

import error
import script

def assert_spend_tx_valid(spend_tx, expected_value, sender_public_key, recipient_public_key, secret_hash):
  """
  Checks the spend TX relayed by the remote peer actually does pay us, and
  contains the right value of coins.
  Raises an exception in case of a problem
  """
  # Inputs must be final.
  for txin in spend_tx.vin:
    if not txin.is_final():
      raise error.TradeError("Spend transaction inputs are not all final.")

  if len(spend_tx.vout) == 0:
    raise error.TradeError("Spend TX does not have any outputs.")

  expected_script = script.build_send_out_script(sender_public_key, recipient_public_key, secret_hash)
  output_matched = False
  for txout in spend_tx.vout:
    if txout.nValue == expected_value and txout.scriptPubKey == expected_script:
      output_matched = True
      break

  if not output_matched:
    raise error.TradeError("Spend TX does not contain a payment to us for the expected number of coins")

def assert_refund_tx_valid(refund_tx, expected_value):
  """
  Checks the refund TX provided by the remote peer matches the expected structure.
  Raises an exception in case of a problem
  """

  lock_min_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=12)
  lock_max_datetime = datetime.datetime.utcnow() + datetime.timedelta(hours=72)
  lock_time = refund_tx.nLockTime
  if lock_time < calendar.timegm(lock_min_datetime.timetuple()):
    raise error.TradeError("Refund TX lock time is "
      + datetime.datetime.utcfromtimestamp(lock_time).strftime('%Y-%m-%d %H:%M:%S')
      + " which is less than 24 hours in the future.")
  if lock_time > calendar.timegm(lock_max_datetime.timetuple()):
    raise error.TradeError("Refund TX lock time is "
      + datetime.datetime.utcfromtimestamp(lock_time).strftime('%Y-%m-%d %H:%M:%S')
      + " which is more than 72 hours in the future.")

  if len(refund_tx.vin) != 1:
    raise error.TradeError("Refund TX does not have exactly one input.")
  if len(refund_tx.vout) != 1:
    raise error.TradeError("Refund TX does not exactly one output.")

  if refund_tx.vout[0].nValue > expected_value:
    raise error.TradeError("Refund TX is for more than the value of the trade.")

  # If the nSequence is 0xffffffff, the transaction can be considered valid
  # despite what the lock time says. Current implementations do not support this,
  # however we want to be sure anyway
  if refund_tx.vin[0].nSequence == 0xffffffff:
    raise error.TradeError("Refund TX input's sequence is final; must be less than MAX_INT.")

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

def build_send_transaction(proxy, quantity, sender_public_key, recipient_public_key, secret_hash, fee_rate):
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
  quantity_inc_fee = quantity + fee_rate.get_fee(1000)
  (txins, total_in) = find_inputs(proxy, quantity_inc_fee)

  txouts = [CTxOut(quantity, script.build_send_out_script(sender_public_key, recipient_public_key, secret_hash))]

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

def build_unsigned_refund_tx(proxy, send_tx, send_tx_n, own_address, nLockTime, fee_rate):
  """
  Generates "TX2" from the guide at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading.
  The same code can also generate TX4. These are the refund transactions in case of
  problems. Transaction outputs are locked to the script:
        Pay w BTC from TX1 to <A's public key>, locked 48 hours in the future, signed by A

  proxy is the RPC proxy to the relevant daemon JSON-RPC interface
  send_tx the (complete, signed) transaction to refund from
  nLockTime the lock time to set on the transaction
  own_address is the private address this client signs the refund transaction with. This must match
      the address provided when generating TX1.

  returns a CMutableTransaction
  """
  prev_txid = send_tx.GetHash()
  prev_out = send_tx.vout[send_tx_n]
  txin = CMutableTxIn(COutPoint(prev_txid, send_tx_n), nSequence=1)

  txin_scriptPubKey = prev_out.scriptPubKey

  fee = fee_rate.get_fee(1000)
  txouts = [CTxOut(prev_out.nValue - fee, own_address.to_scriptPubKey())]

  # Create the unsigned transaction
  return CMutableTransaction([txin], txouts, nLockTime)

"""
Generate the spending transaction for the send TX from the peer

seckey is the secret key used to sign the transaction
"""
def build_receive_tx(proxy, send_tx, recipient_seckey, secret, own_address, fee_rate):
  fee = fee_rate.get_fee(1000)

  prev_txid = send_tx.GetHash()
  prev_out = send_tx.vout[0]
  txin = CMutableTxIn(COutPoint(prev_txid, 0))
  txins = [txin]

  txin_scriptPubKey = prev_out.scriptPubKey

  txouts = [CTxOut(prev_out.nValue - fee, own_address.to_scriptPubKey())]

  # Create the unsigned transaction
  tx = CMutableTransaction(txins, txouts)

  # Calculate the signature hash for that transaction.
  sighash = SignatureHash(txin_scriptPubKey, tx, 0, SIGHASH_ALL)

  # scriptSig needs to be:
  #     <shared secret> 0 <signature> <public key>
  txin.scriptSig = script.build_recv_in_script(recipient_seckey, secret, sighash)

  return tx

def get_refund_tx_sig(recovery_tx, own_seckey, recipient_public_key, other_public_key, secret_hash):
  """
  Generate a signature for the recovery transaction input from the send transaction.

  recipient_public_key is the public key of the peer who receives the coins
  other_public_key is the public key of the peer who confirms the transaction but does not receive any coins
  """
  # Rebuild the input script for the send transaction
  txin_scriptPubKey = script.build_send_out_script(recipient_public_key, other_public_key, secret_hash)

  # Calculate the signature hash for the transaction.
  sighash = SignatureHash(txin_scriptPubKey, recovery_tx, 0, SIGHASH_ALL)

  # Now sign it. We have to append the type of signature we want to the end, in
  # this case the usual SIGHASH_ALL. For some reason appending bytes gives us
  # nonsense, so for now manually stuffing 0x01 on the end
  return own_seckey.sign(sighash) + (b'\x01') # bytes([SIGHASH_ALL])
