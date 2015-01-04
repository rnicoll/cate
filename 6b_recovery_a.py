import json
import os.path
import socket
import sys
import time

from bitcoin import SelectParams
import bitcoin.rpc
from bitcoin.core import *
import bitcoin.core.serialize

from cate import *
from cate.error import ConfigurationError
from cate.fees import CFeeRate
from cate.tx import *

# Scan for transactions where TX1 has been sent to the network,
# but not yet spent, and should be recovered using TX2

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(0)

# Scan the audit directory for transactions ready to spend
for trade_id in os.listdir('audits'):
  directory_path = 'audits' + os.path.sep + trade_id
  if not os.path.isdir(directory_path):
    continue
  if not os.path.isfile(directory_path + os.path.sep + '4_tx2.txt'):
    continue
  if os.path.isfile(directory_path + os.path.sep + '6_complete.txt'):
    continue
  # Load TX2 to find its block time
  with open(directory_path + os.path.sep + '4_tx2.txt', "r") as tx2_file:
    tx2 = CTransaction.deserialize(x(tx2_file.read()))

  if tx2.nLockTime > time.gmtime():
    # Transaction is still locked
    continue

  with open(directory_path + os.path.sep + '2_offer.json', "r") as offer_file:
    offer = json.loads(offer_file.read())
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]

  # Connect to the wallet
  bitcoin.SelectParams(config['daemons'][ask_currency_code]['network'], ask_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  try:
    proxy.sendrawtransaction(tx2)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -25:
        print "TX1 for trade " + trade_id + " has already been spent"
    else:
      raise err

  # Add a file to indicate the TX is complete
  with open(directory_path + os.path.sep + '6_complete.txt', "w", 0700) as completion_file:
    completion_file.write(b2lx(tx2.GetHash()))
