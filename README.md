CATE
====

Cross-chain Atomic Trading Engine

CATE is a multicoin wallet based on the bitcoinj and libdohj libraries.
It currently supports Bitcoin, Litecoin and Dogecoin, and is readily
extensible to support any other currencies compatible with bitcoinj.

CATE is currently in development and should not be used outside of the
test networks until it has significantly further testing. It is likely
there are bugs which result in loss of funds.


Getting Started
---------------

Currently CATE is only suitable for using for development, while testing continues
and the wallet format is finalised. As such, you'll need to download and compile
supporting libraries and CATE in order to run CATE.

You'll need the following installed:

* Git
* Java 8 SDK
* Apache Maven

Then use Git to clone the repositories for, and Maven to install:

* https://github.com/bitcoinj/bitcoinj
* https://github.com/dogecoin/libdohj

Bitcoinj provides support for Bitcoin-like coins, and libdohj then extends it to
add Dogecoin support. To install the libraries, run "mvn install" within the cloned
repository. In both cases the master (default) branch must be used, as CATE requires
the latest code.

Once that is done, clone the CATE repository and run "mvn package" to build it.
This will produce an JAR file "target/cate-0.14-SNAPSHOT-exe.jar", double click
it to run.
