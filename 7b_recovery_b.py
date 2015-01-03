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

# Scan for transactions where TX3 has been sent to the network,
# but not yet spent, and should be recovered using TX4

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
  if not os.path.isfile(directory_path + os.path.sep + '5_tx4.txt'):
    continue
  if os.path.isfile(directory_path + os.path.sep + '7_complete.txt'):
    continue
  # Load TX2 to find its block time
  with open(directory_path + os.path.sep + '5_tx4.txt', "r") as tx4_file:
    tx4 = CTransaction.deserialize(x(tx4_file.read()))

  if tx4.nLockTime > time.gmtime():
    # Transaction is still locked
    continue

  with open(directory_path + os.path.sep + '1_offer.json', "r") as offer_file:
    offer = json.loads(offer_file.read())
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]

  # Connect to the wallet
  bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])

  try:
    proxy.sendrawtransaction(tx4)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -26:
      print "TX3 has already been spent"
    else:
      raise err

  # Add a file to indicate the TX is complete
  with open(directory_path + os.path.sep + '7_complete.txt', "w", 0700) as completion_file:
    completion_file.write(tx4.GetHash())
