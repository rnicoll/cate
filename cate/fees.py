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

class CFeeRate(object):
    def __init__(self, nSatoshisPerK):
        self.nSatoshisPerK = Decimal(nSatoshisPerK)
        
    def get_fee(self, size):
        """ Calculate fee for a transaction
            size should be a number of bytes
            returns fee expressed in Satoshi or other minimum unit
        """
        # TODO: Apply min/max on this
         # Bitcoin Core uses 1000 instead of 1024, matching it here for consistency
        fee = self.nSatoshisPerK * size / 1000

        if fee == 0 and self.nSatoshisPerK > 0:
            fee = nSatoshisPerK

        return fee
