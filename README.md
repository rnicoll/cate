CATE
====

Cross-chain Atomic Trading Engine

CATE is an experiment in trades between two different blockchains. It creates
smart contracts for trustless exchange of cryptocurrency, which are negotiated
via reddit messages. It has been tested with Bitcoin and Dogecoin to this point,
however it should work with any Bitcoin Core 0.9 derived client. These is a good
discussion on the theory underlying this at https://en.bitcoin.it/wiki/Atomic_cross-chain_trading

Please also note that CATE does not yet actually prompt the user to confirm
the transaction suggested by the originating user.

Known Issues
============

Transaction malleability is a risk for this application, in that the refund transactions
are written before their input transactions are confirmed. This means in the transactions
they take inputs from are modified after being relayed, the refund transactions will be
useless. In general this risk is considered minor; transaction malleability does not
allow someone to steal coins, and the trade can still complete successfully even if the
send transactions are modified after being relayed. When BIP62 is introduced on the main
networks it will further reduce this risk.

Currently Bitcoin Core and Dogecoin Core test networks allow relaying of non-final
transactions, which could theoretically enable the refund transactions to be misused
to block spending the send transaction. This is not supported on the main networks
with the reference client, and a fix is being rolled out for the test networks.

Requirements
============

CATE requires both Bitcoin Core and Dogecoin Core wallets, as it bridges between
the two.

CATE requires python-bitcoinlib, which can be found at
https://github.com/petertodd/python-bitcoinlib . It also requires python-altcoinlib
which extends the library to add partial altcoin support, and can be found at
https://github.com/rnicoll/python-altcoinlib

Lastly, CATE requires the "praw" library for accessing reddit. praw can be installed
via "pip"

Setup
=====

CATE has a configuration file which is used to hold authentication details for
accessing reddit, and details of the coins available. A template is provided
called "config.yml.dist", and should be copied to "config.yml".

You should then edit the listed reddit username and password (first two lines).

CATE accesses the Bitcoin Core and Dogecoin Core client software via RPC calls.
To allow it to do this, a username and password must be set in the relevant configuration
files (listed in config.yml). These should be specified using the "rpcuser" and
"rpcpassword" options, as per https://en.bitcoin.it/wiki/Running_Bitcoin

Running CATE
============

First of all, the two wallets must be running and listening for RPC connections.
This can be done either using the daemons (bitcoind and dogecoind), or by providing
the "-server" option when running the Qt client. CATE is hard-coded to work on
the test networks only, so whichever client is used, the "-testnet" option should
be specified as well.

CATE consists of 7 steps, for which there are 9 scripts (7 normal case scripts, 2
recovery). Each peer runs half of the steps (one odd numbered scripts, the other
even numbered scripts).

To start a trade, one user runs the first script "1_make_offer.py", which will
prompt for the trade details then relay them to the specified user over reddit as a
JSON object.

The receiving user then runs "2_accept_offer.py" to scan their messages for trade
offers, and respond. WARNING: Right now, this script does not actually prompt to
check trades are acceptable, it just accepts them outright.

This continues down the rest of the scripts. Step 3 completes the negotiation,
4 & 5 involve coins being sent to the network by each peer, and steps 6 & 7
claim the coins sent in steps 4 & 5.

In case of a problem (i.e. one peer stops trading) after coins are sent, the
two recovery scripts "6b_recovery_a.py" and "7b_recovery_b.py" will send the
recovery transactions to the network. Note that these transactions will only
confirm 48 hours after they were generated (this is to give each peer time
to complete the transaction normally).

Example
=======

As at 4th January 2015, a transaction looks something like the following sequence.
Note that normally each peer handles half of this, so one side would run the
odd numbered commands, the other the even commands. In this case however the transaction
is with myself.

    jrn@Ross-pc:~/Development/cate$ python 1_make_offer.py 
    Okay, first of all I need to know who to make an offer to.
    reddit username: rnicoll
    What currency are you offering, and how much?
    Currency (BTC, LTC, DOGE): BTC
    Quantity offered: 0.01
    What currency are you wanting, and how much?
    Currency (BTC, LTC, DOGE): DOGE
    Quantity asked: 10000
    Offer sent

    jrn@Ross-pc:~/Development/cate$ python 2_accept_offer.py 
    Received offer 0faeddcb-e190-46f7-9e0f-ae8c70e01529 of 0.01000000 BTC for 10000.00000000 DOGE

    jrn@Ross-pc:~/Development/cate$ python 3_accept_offer_response.py 

    jrn@Ross-pc:~/Development/cate$ python 4_send_coins_a.py

    jrn@Ross-pc:~/Development/cate$ python 5_send_coins_b.py

    jrn@Ross-pc:~/Development/cate$ python 6_recv_coins_a.py 

    jrn@Ross-pc:~/Development/cate$ python 7_recv_coins_b.py 
    Spending coins from trade 0faeddcb-e190-46f7-9e0f-ae8c70e01529

Audit
=====

A number of files are generated under the "audits" directory, within a
directory named based on the trade ID. These exist to allow the process
to be checked and any problems recovered from. An example for the
curious is provided under the "audit_example" folder, although with
the two private key files ("1_private_key.txt" and "2_private_key.txt")
removed.

