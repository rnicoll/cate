import error

import bitcoin
from bitcoin.core import *
from bitcoin.core.script import *

def extract_secret_from_receive_script(tx, vin):
  """
  Searches for the secret from the scriptSig of the given input
  """
  # Extract the secret from the transaction output script
  sig_elements = []
  for sig_element in vin.scriptSig:
    sig_elements.append(sig_element)
  if len(sig_elements) < 3:
    raise error.TradeError("Cannot extract shared secret from scriptSig of transaction " + b2x(tx.GetHash()))
  public_key_a = sig_elements.pop()
  sig_a = sig_elements.pop()
  tx_type = sig_elements.pop()

  # TODO: Should verify the public key and signature are as expected
  if tx_type == 0:
    # If the transaction was refunded back to its sender, rather than spent
    raise error.TradeError("Signatures on transaction " + b2x(tx.GetHash()) + " are from the refund TX, not the spending TX.")

  return sig_elements.pop()

def build_send_out_script(sender_public_key, recipient_public_key, secret_hash):
  """
  Generates the script coins are sent to at the start of the trade.
  """

  # scriptSig is either:
  #     <recipient signature> <recipient public key hash> 0 <sender signature> <sender public key hash>
  # or
  #     <recipient signature> <recipient public key hash> 1 <shared secret>
  return CScript(
    [
      OP_DUP, OP_HASH160, bitcoin.core.Hash160(recipient_public_key), OP_EQUALVERIFY, OP_CHECKSIGVERIFY,
      OP_IF,
        # Top of stack is not zero, script matches a pair of signatures
        OP_DUP, OP_HASH160, bitcoin.core.Hash160(sender_public_key), OP_EQUALVERIFY, OP_CHECKSIG,
      OP_ELSE,
        # Secret and single signature
        OP_HASH256, secret_hash, OP_EQUAL,
      OP_ENDIF
    ]
  )

def build_recovery_in_script(other_sig, other_public_key, recipient_sig, recipient_public_key):
  """
  Generates the script for performing a recovery transaction from the initial
  transaction.
  """
  return CScript([recipient_sig, recipient_public_key, 1, other_sig, other_public_key])

"""
Generate the transaction signature for receiving funds from the trade

recipient_seckey is the secret key used to sign the transaction
secret is the shared secret used to unlock the input
sighash is the hash of the transaction the script is part of
"""
def build_recv_in_script(recipient_seckey, secret, sighash):
  sig = recipient_seckey.sign(sighash) + (b'\x01') # bytes([SIGHASH_ALL])
  return CScript([secret, 0, sig, recipient_seckey.pub])
