import praw
import json
import os.path
import socket
import sys

from bitcoin import SelectParams
from bitcoin.core import *
import bitcoin.rpc
import bitcoin.core.scripteval
from cate import *
from cate.error import ConfigurationError, MessageError, TradeError
from cate.tx import *

def assert_confirmation_valid(confirmation):
  if 'trade_id' not in confirmation:
    raise MessageError( "Missing trade ID from confirmed offer.")
  if confirmation['trade_id'].find(os.path.sep) != -1:
    raise MessageError("Invalid trade ID received; trade must not contain path separators")
  if 'tx2_sig' not in confirmation:
    raise MessageError( "Missing TX2 signature from confirmed offer.")
  if 'tx4' not in confirmation:
    raise MessageError( "Missing TX4 refund transaction from confirmed offer.")

def process_offer_confirmed(confirmation, audit):
  """
  1. Receives the completed TX2 and the partial TX4 from 'B'.
  2. Verifies the signatures on TX2 are valid
  3. Verifies the lock time on TX4 is valid
  4. Signs TX4 to complete it
  5. Returns TX4 to 'B'
  6. Submits TX1 to the network
  """
  offer = audit.load_json('2_offer.json')
  acceptance = audit.load_json('2_acceptance.json')

  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]
  ask_currency_quantity = offer['ask_currency_quantity']
  private_key_a = audit.load_private_key('2_private_key.txt')
  public_key_a = bitcoin.core.key.CPubKey(private_key_a._cec_key.get_pubkey())
  public_key_b = bitcoin.core.key.CPubKey(x(offer['public_key_b']))
  secret_hash = x(acceptance['secret_hash'])

  # Connect to the daemon
  # TODO: Check the configuration exists
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  tx1 = audit.load_tx('2_tx1.txt')
  tx2 = CMutableTransaction.from_tx(CTransaction.deserialize(x(acceptance['tx2'])))
  tx4 = CTransaction.deserialize(x(confirmation['tx4']))

  # Apply signatures to TX2 and check the result is valid
  txin_scriptPubKey = tx1.vout[0].scriptPubKey
  sighash = SignatureHash(txin_scriptPubKey, tx2, 0, SIGHASH_ALL)
  tx2_sig_a = private_key_a.sign(sighash) + (b'\x01') # Append signature hash type
  tx2_sig_b = x(confirmation['tx2_sig'])
  if not public_key_b.verify(sighash, tx2_sig_b):
    raise TradeError("Signature from peer for TX2 is invalid.")

  tx2.vin[0].scriptSig = CScript([tx2_sig_a, public_key_a, 1, tx2_sig_b, public_key_b])
  bitcoin.core.scripteval.VerifyScript(tx2.vin[0].scriptSig, txin_scriptPubKey, tx2, 0, (SCRIPT_VERIFY_P2SH,))
  audit.save_tx('4_tx2.txt', tx2)

  # Verify the TX4 returned by the peer, then sign it
  assert_tx2_valid(tx4)
  tx4_sig = get_tx4_signature(proxy, tx4, private_key_a, public_key_a, public_key_b, secret_hash)

  proxy.sendrawtransaction(tx1)

  # Pass back the signature
  return {
    'trade_id': trade_id,
    'tx4_sig': b2x(tx4_sig)
  }

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(0)

r = praw.Reddit(user_agent = USER_AGENT)
try:
  reddit_login(r, config)
except ConfigurationError as e:
  print e
  sys.exit(0)

for message in r.get_messages():
  if message.subject != 'CATE transaction confirmed (3)':
    continue
  confirmation = json.loads(message.body)
  try:
    assert_confirmation_valid(confirmation)
  except MessageError as err:
    print("Received invalid trade from " + message.author.name)
    continue
  trade_id = confirmation['trade_id']
  audit = TradeDao(trade_id)
  if audit.file_exists('4_confirmation.json'):
    print "Offer confirmation " + trade_id + " already received, ignoring offer"
    continue

  # Record the received response
  audit.save_json('4_confirmation.json', confirmation)

  try:
    response = process_offer_confirmed(confirmation, audit)
  except socket.error as err:
    print "Could not connect to wallet."
    sys.exit(1)
  if not response:
    break

  audit.save_json('4_coins_sent.json', response)

  r.send_message(message.author, 'CATE transaction sent (4)', json.dumps(response))
