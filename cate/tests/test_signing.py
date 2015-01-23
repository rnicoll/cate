from __future__ import absolute_import

import os
import unittest

from bitcoin.core import b2x, x, CMutableTransaction, CTxOut, Hash
from bitcoin.core.script import SIGHASH_ALL, SignatureHash, CScript
from bitcoin.core.scripteval import MissingOpArgumentsError, VerifyOpFailedError, VerifyScript, SCRIPT_VERIFY_P2SH
from bitcoin.wallet import CBitcoinSecret, P2PKHBitcoinAddress
from cate.fees import CFeeRate
from cate.script import build_send_out_script
from cate.tx import build_signed_refund_tx, build_unsigned_refund_tx, get_refund_tx_sig

class TestRecoverySignature(unittest.TestCase):
  def test_build_send_script(self):
    """ Run simple sanity checks on script generation """

    # Set up constants for this test
    sender_private_key = CBitcoinSecret.from_secret_bytes(x('4f65da9b656de4036076911707d2b2dbf065689245b8674faa8790e36d7e5850'))
    sender_public_key = sender_private_key.pub
    recipient_private_key = CBitcoinSecret.from_secret_bytes(x('cfe8e33672f7045f020210f3c7afbca660e053e4c9415c542ff185e97b175cf0'))
    recipient_public_key = recipient_private_key.pub
    send_to_key = CBitcoinSecret.from_secret_bytes(x('15a249b4c09286b877d4708191f1ee8de09903bae034dd9dc8e3286451fa1c80'))
    send_to_address = P2PKHBitcoinAddress.from_pubkey(send_to_key.pub)
    secret = x('88d6e51f777b0b8dc0f429da9f372fbc')
    secret_hash = Hash(secret)
    quantity = 1000

    # Build the send transaction
    txins = [] # TODO: Provide some random inputs
    txouts = [CTxOut(quantity, build_send_out_script(sender_public_key, recipient_public_key, secret_hash))]
    send_tx = CMutableTransaction(txins, txouts)
    send_tx_n = 0 # We're working with the first transaction input

    # Build the refund transaction
    nLockTime = 1422177943
    refund_tx = build_unsigned_refund_tx(send_tx, send_tx_n, send_to_address, nLockTime, CFeeRate(0))

    # Actually verify the signatures
    sighash = SignatureHash(send_tx.vout[0].scriptPubKey, refund_tx, 0, SIGHASH_ALL)
    sender_sig = get_refund_tx_sig(refund_tx, sender_private_key, sender_public_key, recipient_public_key, secret_hash)
    self.assertTrue(sender_public_key.verify(sighash, sender_sig[:-1]))
    recipient_sig = get_refund_tx_sig(refund_tx, recipient_private_key, sender_public_key, recipient_public_key, secret_hash)
    self.assertTrue(recipient_public_key.verify(sighash, recipient_sig[:-1]))

    # Test building a complete refund transaction
    refund_tx = build_signed_refund_tx(send_tx, send_tx_n, refund_tx,
      recipient_sig, recipient_public_key,
        sender_private_key, secret_hash)
    # This throws an exception in case of a problem
    VerifyScript(refund_tx.vin[0].scriptSig,
      send_tx.vout[send_tx_n].scriptPubKey, refund_tx, 0, (SCRIPT_VERIFY_P2SH,))
