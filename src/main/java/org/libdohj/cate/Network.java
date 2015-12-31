/*
 * Copyright 2015 jrn.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.libdohj.cate;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;

import com.google.common.util.concurrent.Service;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.SendResult;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectionEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.WalletCoinEventListener;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.store.BlockStoreException;
import org.libdohj.cate.controller.MainController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

/**
 * Class which manages incoming events and knows which network they apply
 * to. In a conventional software wallet, this would be part of the main UI,
 * however here because we have multiple independent networks we expose them
 * as an API for the UI to aggregate.
 *
 * Each network has its own thread both so that bitcoinj context (which is held
 * in a thread store) is always correct for the network, and so that changes to
 * the contained wallet are guaranteed to be done one at a time. As such many
 * calls here take callbacks which are called once work is completed.
 *
 * While in theory we could make this an Executor and then share it with
 * WalletAppKit, it's useful to know that any work sent to the network is
 * executed in-order.
 */
public class Network extends Thread implements NewBestBlockListener, PeerDataEventListener, PeerConnectionEventListener, WalletCoinEventListener {
    /**
     * Interval between checking of the network has been told to shut down.
     */
    public static final long POLL_INTERVAL_MILLIS = 250;
    private static final int WORK_QUEUE_SIZE = 20;

    private final File dataDir;
    private final NetworkParameters params;
    private WalletAppKit kit;
    private boolean synced = false;
    private final MainController controller;
    private final Logger logger = LoggerFactory.getLogger(Network.class);

    /** Work queue for the thread once the kit has been started */
    private final ArrayBlockingQueue<Work> workQueue = new ArrayBlockingQueue<>(WORK_QUEUE_SIZE);
    private boolean cancelled = false;

    private final SimpleObjectProperty<String> balance = new SimpleObjectProperty<>("0");
    private final SimpleIntegerProperty blocks = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty blocksLeft = new SimpleIntegerProperty(0);
    /** Copy of the wallet encrypted state which we update if we change */
    private final SimpleBooleanProperty encrypted = new SimpleBooleanProperty();
    private final SimpleIntegerProperty peerCount = new SimpleIntegerProperty(0);

    /** Transactions we've been notified of, either via onCoinsSent()
     * or onCoinsReceived(). Used so we can filter out transactions that fire
     * both events (i.e. a transaction that pays out, but also has change
     * paying back to us).
     */
    private final Set<Transaction> seenTransactions = new HashSet<>();

    /**
     * @param params network this manages.
     * @param controller the controller to push events back to.
     * @param dataDir the data directory to store the wallet and SPV chain in.
     */
    public Network(final NetworkParameters params, final MainController controller,
            final File dataDir) {
        this.controller = controller;
        this.params = params;
        this.dataDir = dataDir;
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
        if (blocksLeft <= 0) {
            synced = true;
        }
        this.blocksLeft.set(blocksLeft);
    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        this.blocksLeft.set(blocksLeft);
    }

    @Override
    public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
        this.blocks.set(block.getHeight());
    }

    @Override
    public Message onPreMessageReceived(Peer peer, Message m) {
        return null;
    }

    @Override
    public List<Message> getData(Peer peer, GetDataMessage m) {
        return null;
    }

    @Override
    public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
    }

    @Override
    public void onPeerConnected(Peer peer, int peerCount) {
        this.peerCount.set(peerCount);
    }

    @Override
    public void onPeerDisconnected(Peer peer, int peerCount) {
        this.peerCount.set(peerCount);
    }

    @Override
    public void onCoinsReceived(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        if (seenTransactions.add(tx)) {
            controller.addTransaction(params, tx, prevBalance, newBalance);
        }
        balance.set(newBalance.toPlainString());
    }

    @Override
    public void onCoinsSent(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        if (seenTransactions.add(tx)) {
            controller.addTransaction(params, tx, prevBalance, newBalance);
        }
        balance.set(newBalance.toPlainString());
        // TODO: Update the displayed receive address
    }

    /**
     * @return the wallet kit
     */
    private WalletAppKit getKit() {
        return kit;
    }

    public ObservableValue<String> getObservableBalance() {
        return balance;
    }

    public ObservableValue<Number> getObservableBlocks() {
        return blocks;
    }

    public ObservableValue<Number> getObservableBlocksLeft() {
        return blocksLeft;
    }

    public ObservableBooleanValue getObservableEncryptedState() {
        return encrypted;
    }

    public ObservableValue<Number> getObservablePeerCount() {
        return peerCount;
    }

    public NetworkParameters getParams() {
        return params;
    }

    @Override
    public void run() {
        kit = new WalletAppKit(params, dataDir, "cate_" + params.getId()) {
            @Override
            protected void onSetupCompleted() {
                peerGroup().setConnectTimeoutMillis(1000);
                peerGroup().addDataEventListener(Network.this);
                peerGroup().addConnectionEventListener(Network.this);
                chain().addNewBestBlockListener(Network.this);
                wallet().addCoinEventListener(Network.this);

                Network.this.workQueue.offer((Work) () -> {
                    balance.set(Network.this.getKit().wallet().getBalance().toPlainString());
                    try {
                        blocks.set(Network.this.getKit().store().getChainHead().getHeight());
                    } catch (BlockStoreException ex) {
                        logger.error("Error getting current chain head while starting wallet "
                            + params.getId(), ex);
                    }
                    encrypted.set(wallet().isEncrypted());
                    controller.registerWallet(Network.this, Network.this.getKit().wallet());
                });
            }
        };
        kit.setAutoStop(false);
        kit.setBlockingStartup(false);
        kit.startAsync();

        while (!cancelled) {
            final Work work;
            try {
                work = Network.this.workQueue.poll(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
            } catch(InterruptedException ex) {
                // Loop around to test for cancelled state again automatically
                continue;
            }
            if (null != work) {
                try {
                    work.run();
                } catch(Exception ex) {
                    // TODO: Now what!?
                    logger.error("Error running work for network "
                        + params.getId(), ex);
                    this.controller.handleNetworkError(this, ex);
                }
            }
        }
    }

    /**
     * Cancel the network task. This does not interrupt the thread, but instead
     * relies on timeouts to ensure the cancellation is seen quickly.
     * 
     * @return the stopped kit service.
     */
    public Service cancel() {
        this.cancelled = true;
        final Service service = kit.stopAsync();
        return service;
    }

    /**
     * Format a coin amount.
     */
    public CharSequence format(Coin amount) {
        return params.getMonetaryFormat().format(amount);
    }

    /**
     * Queue a request to decrypt this wallet. This returns immediately,
     * as the actual work is done on the network thread in order to ensure the
     * thread context is correct. Unhandled errors are reported back to Network.
     * 
     * @param password password to decrypt the wallet with
     * @param onSuccess callback on success
     * @param onWalletNotEncrypted callback if the wallet is not encrypted
     * @param onCrypterError callback in case of an error in the key crypter
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void decrypt(String password, Consumer<Object> onSuccess,
                        Consumer<Object> onWalletNotEncrypted,
                        Consumer<KeyCrypterException> onCrypterError,
                        final long timeout, final TimeUnit timeUnit)
            throws InterruptedException {
        this.workQueue.offer((Work) () -> {
            final Wallet wallet = kit.wallet();
            if (!wallet.isEncrypted()) {
                onCrypterError.accept(null);
            } else {
                final KeyCrypter keyCrypter = kit.wallet().getKeyCrypter();

                if (keyCrypter == null) {
                    this.controller.handleNetworkError(this, new IllegalStateException("Wallet is encrypted but has no key crypter."));
                } else {
                    try {
                        kit.wallet().decrypt(keyCrypter.deriveKey(password));
                        encrypted.set(false);
                        onSuccess.accept(null);
                    } catch(KeyCrypterException ex) {
                        onCrypterError.accept(ex);
                    }
                }
            }
        });
    }

    /**
     * Queue a request to encrypt this wallet. This returns immediately,
     * as the actual work is done on the network thread in order to ensure the
     * thread context is correct.
     * 
     * @param password password to encrypt the wallet with
     * @param onSuccess handler to be called on success
     * @param onWalletEncrypted callback if the wallet is not encrypted
     * @param onCrypterError callback in case of an error in the key crypter
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void encrypt(final String password, final Consumer<Object> onSuccess,
                        Consumer<Object> onWalletEncrypted,
                        Consumer<KeyCrypterException> onCrypterError,
                        final long timeout, final TimeUnit timeUnit)
            throws InterruptedException {
        this.workQueue.offer((Work) () -> {
            final Wallet wallet = kit.wallet();
            if (wallet.isEncrypted()) {
                onWalletEncrypted.accept(null);
            } else {
                KeyCrypter keyCrypter = kit.wallet().getKeyCrypter();

                if (keyCrypter == null) {
                    keyCrypter = new KeyCrypterScrypt();
                }

                try {
                    kit.wallet().encrypt(keyCrypter, keyCrypter.deriveKey(password));
                    encrypted.set(true);
                    onSuccess.accept(null);
                } catch(KeyCrypterException ex) {
                    onCrypterError.accept(ex);
                }
            }
        });
    }

    /**
     * Queue a request to send coins to the given address. This returns immediately,
     * as the actual work is done on the network thread in order to ensure the
     * thread context is correct.
     *
     * @param req the send coin request to pass to the wallet
     * @param onSuccess handler to be called on success
     * @param onInsufficientFunds handler to be called if the user lacks sufficient funds
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void sendCoins(final Wallet.SendRequest req,
                          final Consumer<SendResult> onSuccess,
                          final Consumer<Coin> onInsufficientFunds,
                          final Consumer<KeyCrypterException> onWalletLocked,
                          final long timeout, final TimeUnit timeUnit)
            throws InterruptedException {
        this.workQueue.offer((Work) () -> {
            // TODO: Calculate fees in a network-appropriate way
            final SendResult result;
            try {
                result = Network.this.getKit().wallet().sendCoins(req);
                onSuccess.accept(result);
            } catch (InsufficientMoneyException ex) {
                onInsufficientFunds.accept(ex.missing);
            } catch(KeyCrypterException ex) {
                onWalletLocked.accept(ex);
            } finally {
                // Wipe the key to ensure if it's stored in insecure memory, it's all
                // zeroes on disk
                Arrays.fill(req.aesKey.getKey(), (byte) 0);
            }
        });
    }

    /**
     * Get a key parameter derived from the given password. This only works if
     * the wallet is, or previously has been, encrypted.
     *
     * @param password the password to derive an AES key from.
     * @return the derived AES key.
     * @throws IllegalStateException if the wallet is not encrypted
     */
    public KeyParameter getKeyFromPassword(String password) throws IllegalStateException {
        final KeyCrypter keyCrypter = kit.wallet().getKeyCrypter();
        if (keyCrypter == null) {
            return keyCrypter.deriveKey(password);
        } else {
            throw new IllegalStateException("Wallet does not have a key crypter.");
        }
    }

    private interface Work {
        public void run() throws Exception;
    }
}
