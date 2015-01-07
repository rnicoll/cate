import calendar
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

# Scan for transactions where TX3 has been sent to the network,
# but not yet spent, and should be recovered using TX4

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print e
  sys.exit(0)

# Scan the audit directory for transactions ready to spend
for trade_id in os.listdir('audits'):
  audit = TradeDao(trade_id)
  if not audit.file_exists('5_tx4.txt'):
    continue
  if audit.file_exists('7_complete.txt'):
    continue
  # Load TX2 to find its block time
  tx4 = audit.load_tx('5_tx4.txt')
  if tx4.nLockTime > calendar.timegm(time.gmtime()):
    # Transaction is still locked
    continue

  offer = audit.load_json('1_offer.json')
  offer_currency_code = NETWORK_CODES[offer['offer_currency_hash']]

  # Connect to the wallet
  bitcoin.SelectParams(config['daemons'][offer_currency_code]['network'], offer_currency_code)
  proxy = bitcoin.rpc.Proxy(service_port=config['daemons'][offer_currency_code]['port'], btc_conf_file=config['daemons'][offer_currency_code]['config'])

  try:
    proxy.sendrawtransaction(tx4)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -25:
      print "TX3 has already been spent"
    if err.error['code'] == -26:
        print "Refund transaction is not yet final; please wait 48 hours after the start of the trade"
        continue
    if err.error['code'] == -27:
        print "Refund transaction " + b2lx(tx4.GetHash()) + " has already been sent"
    else:
      raise err

  # Add a file to indicate the TX is complete
  audit.save_text('7_complete.txt', b2lx(tx4.GetHash()))
