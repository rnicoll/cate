import math
import os.path
import sys
import time

import error

from bitcoin.core import *
from bitcoin.core.script import *
import bitcoin.core.serialize

def find_txin_matching_output(tx, vout):
  """
  Searches the given block for a transaction with the input provided

  Returns the TX if a match is found, or None otherwise
  """
  for inpoint in tx.vin:
    prevout = inpoint.prevout
    if prevout.hash == vout.hash and prevout.n == vout.n:
      return inpoint
  return None

def find_tx_matching_id(block, tx_id):
  """
  Searches the given block for a transaction with the input provided

  Returns the TX if a match is found, or None otherwise
  """
  for tx in block.vtx:
    if tx.GetHash() == tx_id:
      return tx
  return None

def find_tx_matching_output(block, vout):
  """
  Searches the given block for a transaction which includes the given output
  in its inputs

  Returns the TX if a match is found, or None otherwise
  """
  for tx in block.vtx:
    txin = find_txin_matching_output(tx, vout)
    if txin:
      return tx
  return None

def get_first_block(proxy, not_before_time):
  """
  Estimates the first block which could reasonably contain a transaction
  sent at the given time.

  Returns a tuple of block height and the first block

  """
  try:
    # introduced in Bitcoin Core v 0.9.2  
    blockchain_info = proxy.getblockchaininfo()
    height= blockchain_info['blocks']
    bestblockhash= lx(blockchain_info['bestblockhash'])
  except bitcoin.rpc.JSONRPCException as e:
    height=proxy.getblockcount()
    bestblockhash=proxy.getblockhash(height)
    
  # Figure out approximate block interval by grabbing the last block, and ten blocks ago
  prev_height = max(0, height - 10)
  block_top = proxy.getblock(bestblockhash)
  block_prev = proxy.getblock(proxy.getblockhash(prev_height))

  if block_top.nTime > not_before_time:
    block_time = max(1, (block_top.nTime - block_prev.nTime) / (height - prev_height))
    blocks_to_go_back = min(height, int(math.ceil((block_top.nTime - not_before_time) / block_time)))
  else:
    # No blocks since the transaction completed, so we start at the top of the chain
    blocks_to_go_back = 0
  first_height = height - blocks_to_go_back

  first_block = proxy.getblock(proxy.getblockhash(first_height))
  while first_block.nTime > not_before_time:
    first_height -= 1
    first_block = proxy.getblock(proxy.getblockhash(first_height))

  return (first_height, first_block)

def wait_for_tx_to_confirm(proxy, audit, tx_id, not_before_time):
  """
  Waits for a transaction with the given ID to be confirmed on the blockchain.
  """
  # TODO: This should have a timeout
  (height, block) = get_first_block(proxy, not_before_time)
  height += 1
  tx = find_tx_matching_id(block, tx_id)
  while not tx:
    try:
      block = proxy.getblock(proxy.getblockhash(height))
      height += 1
    except IndexError as err:
      time.sleep(5)
      continue
    tx = find_tx_matching_id(block, tx_id)

  return tx
