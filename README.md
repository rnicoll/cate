Cate
====

Cate (Cross-chain Atomic Trading Engine) is a multicoin wallet based on the
bitcoinj and libdohj libraries. It currently supports Bitcoin, Litecoin and
Dogecoin, and is readily extensible to support any other currencies compatible
with bitcoinj.

Cate is currently in development and should not be used outside of the
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

Contributing
------------

Contributions to the code and documentation are greatly appreciated. A list of
tasks needing work is maintained on Github at
[https://github.com/rnicoll/cate/issues](https://github.com/rnicoll/cate/issues),
but obviously do feel free to submit other improvements. Contributions should
be raised as a pull request against the project for code review.

Code style should follow the [Google Java guidelines](https://google.github.io/styleguide/javaguide.html),
and in cases where existing code does not conform to those guidelines, please
raise a pull request to correct the style. Please however raise this as its own
pull request, do not mix with other pull requests, so it's clear why code has
been changed.

Roadmap
-------

The initial release is targeted for the bitcoinj 0.14 release, and will be a
simple wallet only. Later versions will add functionality for cross-chain trades
(i.e. the core of a decentralised exchange).
