# Raised in case of an issue with the configuration
class ConfigurationError(Exception):
  def __init__(self, value):
    self.value = value
  def __str__(self):
    return repr(self.value)

# Raised in case of an issue funding funds to spend
class FundsError(Exception):
  def __init__(self, value):
    self.value = value
  def __str__(self):
    return repr(self.value)

# Raised in case of an issue with the message from the remote peer
class MessageError(ValueError):
  def __init__(self, value):
    self.value = value
  def __str__(self):
    return repr(self.value)

# Raised in case of an issue with the trade from the remote peer,
# such as a transaction is invalid
class TradeError(ValueError):
  def __init__(self, value):
    self.value = value
  def __str__(self):
    return repr(self.value)
