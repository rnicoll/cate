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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import com.google.common.util.concurrent.Service;
import java.util.Arrays;
import java.util.HashSet;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.SendResult;
import org.bitcoinj.core.listeners.PeerConnectionEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.WalletCoinEventListener;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.store.BlockStoreException;
import org.libdohj.cate.controller.MainController;
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
 */
public class Network extends Thread implements PeerDataEventListener, PeerConnectionEventListener, WalletCoinEventListener {
    /**
     * Interval between checking of the network has been told to shut down.
     */
    public static final long POLL_INTERVAL_MILLIS = 250;
    private static final int WORK_QUEUE_SIZE = 20;

    private final NetworkParameters params;
    private WalletAppKit kit;
    private boolean synced = false;
    private final MainController controller;

    /** Crypter used to convert wallet passwords to AES keys */
    private final KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt();

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
    private Set<Transaction> seenTransactions = new HashSet<>();

    public Network(NetworkParameters params, final MainController controller) {
        this.controller = controller;
        this.params = params;
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
        if (blocksLeft <= 0) {
            synced = true;
        }
        try {
            this.blocks.set(kit.store().getChainHead().getHeight());
        } catch (BlockStoreException ex) {
            // TODO: Log
            System.err.println(ex.toString());
            ex.printStackTrace();
        }
        this.blocksLeft.set(blocksLeft);
    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        this.blocksLeft.set(blocksLeft);
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

    /**
     * Converts a password to an AES key.
     */
    public KeyParameter getKeyFromPassword(String password) {
        return keyCrypter.deriveKey(password);
    }

    @Override
    public void run() {
        kit = new WalletAppKit(params, new File("."), "cate_" + params.getId()) {
            @Override
            protected void onSetupCompleted() {
                peerGroup().setConnectTimeoutMillis(1000);
                peerGroup().addDataEventListener(Network.this);
                peerGroup().addConnectionEventListener(Network.this);
                wallet().addCoinEventListener(Network.this);

                Network.this.workQueue.offer((Work) () -> {
                    balance.set(Network.this.getKit().wallet().getBalance().toPlainString());
                    try {
                        blocks.set(Network.this.getKit().store().getChainHead().getHeight());
                    } catch (BlockStoreException ex) {
                        // TODO: Log
                        System.err.println(ex.toString());
                        ex.printStackTrace();
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
                work.run();
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
        final Service service = kit.stopAsync();
        this.cancelled = true;
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
     * thread context is correct.
     * 
     * @param password password to decrypt the wallet with
     * @param onSuccess handler to be called on success
     * @param onError callback in case of an error
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void decrypt(String password, Consumer<Object> onSuccess, Consumer<Exception> onError,
                        final long timeout, final TimeUnit timeUnit)
            throws InterruptedException {
        this.workQueue.offer((Work) () -> {
            final Wallet wallet = kit.wallet();
            if (!wallet.isEncrypted()) {
                onError.accept(new WalletNotEncryptedException());
            } else {
                try {
                    kit.wallet().decrypt(keyCrypter.deriveKey(password));
                    encrypted.set(false);
                    onSuccess.accept(null);
                } catch(KeyCrypterException ex) {
                    onError.accept(ex);
                } catch(Exception ex) {
                    // TODO: Should we have a separate callback for crypter and non-crypter errors?
                    onError.accept(ex);
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
     * @param onError callback in case of an error
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void encrypt(final String password, final Consumer<Object> onSuccess, final Consumer<Exception> onError,
                        final long timeout, final TimeUnit timeUnit)
            throws InterruptedException {
        this.workQueue.offer((Work) () -> {
            final Wallet wallet = kit.wallet();
            if (wallet.isEncrypted()) {
                onError.accept(new WalletAlreadyEncryptedException());
            } else {
                try {
                    kit.wallet().encrypt(keyCrypter, keyCrypter.deriveKey(password));
                    encrypted.set(true);
                    onSuccess.accept(null);
                } catch(KeyCrypterException ex) {
                    onError.accept(ex);
                } catch(Exception ex) {
                    // TODO: Should we have a separate callback for crypter and non-crypter errors?
                    onError.accept(ex);
                }
            }
        });
    }

    /**
     * Queue a request to send coins to the given address. This returns immediately,
     * as the actual work is done on the network thread in order to ensure the
     * thread context is correct.
     *
     * @param onSuccess handler to be called on success
     * @param onInsufficientFunds handler to be called if the user lacks sufficient funds
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void sendCoins(final Wallet.SendRequest req,
                          final Consumer<SendResult> onSuccess,
                          final Consumer<Coin> onInsufficientFunds,
                          final Consumer<KeyCrypterException> onWalletLocked,
                          final Consumer<Exception> onError,
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
            } catch(Exception ex) {
                onError.accept(ex);
            } finally {
                // Wipe the key to ensure if it's stored in insecure memory, it's all
                // zeroes on disk
                Arrays.fill(req.aesKey.getKey(), (byte) 0);
            }
        });
    }

    private static class WalletAlreadyEncryptedException extends Exception { }
    private static class WalletNotEncryptedException extends Exception { }

    private interface Work {
        public void run();
    }
}
