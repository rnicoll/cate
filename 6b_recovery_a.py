import calendar
import json
import os.path
import socket
import sys
import time

from altcoin import SelectParams
import altcoin.rpc
import bitcoin.rpc

from cate import *
from cate.error import ConfigurationError

# Scan for transactions where TX1 has been sent to the network,
# but not yet spent, and should be recovered using TX2

try:
  config = load_configuration("config.yml")
except ConfigurationError as e:
  print (e)
  sys.exit(0)

# Scan the audit directory for transactions ready to spend
for trade_id in os.listdir('audits'):
  audit = TradeDao(trade_id)
  if not audit.file_exists('4_tx2.txt'):
    continue
  if audit.file_exists('6_complete.txt'):
    continue
  # Load TX2 to find its block time
  tx2 = audit.load_tx('4_tx2.txt')
  if tx2.nLockTime > calendar.timegm(time.gmtime()):
    # Transaction is still locked
    continue

  offer = audit.load_json('2_offer.json')
  ask_currency_code = NETWORK_CODES[offer['ask_currency_hash']]

  # Connect to the wallet
  altcoin.SelectParams(offer['ask_currency_hash'])
  proxy = altcoin.rpc.AltcoinProxy(service_port=config['daemons'][ask_currency_code]['port'], btc_conf_file=config['daemons'][ask_currency_code]['config'])

  try:
    proxy.sendrawtransaction(tx2)
  except bitcoin.rpc.JSONRPCException as err:
    if err.error['code'] == -25:
        print ("TX1 for trade " )+ trade_id + " has already been spent"
    if err.error['code'] == -26:
        print ("Refund transaction is not yet final); please wait 48 hours after the start of the trade"
        continue
    if err.error['code'] == -27:
        print ("Refund transaction " )+ b2lx(tx2.GetHash()) + " has already been sent"
    else:
      raise err

  # Add a file to indicate the TX is complete
  audit.save_text('6_complete.txt', b2lx(tx2.GetHash()))
