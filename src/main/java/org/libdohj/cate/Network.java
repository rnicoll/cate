/*
 * Copyright 2015, 2016 Ross Nicoll.
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;

import com.google.common.util.concurrent.Service;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
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
import org.bitcoinj.core.listeners.WalletEventListener;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.libdohj.cate.controller.MainController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

/**
 * Class which manages incoming events and knows which network they apply to. In
 * a conventional software wallet, this would be part of the main UI, however
 * here because we have multiple independent networks we expose them as an API
 * for the UI to aggregate.
 *
 * Each network has its own executor both so that bitcoinj context (which is
 * held in a thread store) is always correct for the network, and so that
 * changes to the contained wallet are guaranteed to be done in sequence but
 * without blocking UI. As such many calls here take callbacks which are called
 * once work is completed.
 */
public class Network extends WalletAppKit {

    private final EventBridge eventBridge;
    private final MainController controller;
    private final Logger logger = LoggerFactory.getLogger(Network.class);

    private final SimpleStringProperty estimatedBalance = new SimpleStringProperty("0");
    private final SimpleIntegerProperty blocks = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty blocksLeft = new SimpleIntegerProperty(0);
    /**
     * Copy of the wallet encrypted state which we update if we change
     */
    private final SimpleBooleanProperty encrypted = new SimpleBooleanProperty();
    private final SimpleIntegerProperty peerCount = new SimpleIntegerProperty(0);

    /**
     * Transactions we've been notified of, either via onCoinsSent() or
     * onCoinsReceived(). Used so we can filter out transactions that fire both
     * events (i.e. a transaction that pays out, but also has change paying back
     * to us).
     */
    private final Set<Transaction> seenTransactions = new HashSet<>();

    private final Executor networkExecutor;

    /**
     * @param context context this network manages.
     * @param controller the controller to push events back to.
     * @param directory the data directory to store the wallet and SPV chain in.
     * @param networkExecutor executor for tasks belonging to this network.
     * Must exist after the lifecycle of network (so that service status listeners
     * can be attached to it).
     */
    public Network(final Context context, final MainController controller,
            final File directory, final Executor networkExecutor) {
        super(context, directory, "cate_" + context.getParams().getId());
        this.controller = controller;
        this.networkExecutor = networkExecutor;
        eventBridge = new EventBridge();
        autoStop = false;
        blockingStartup = true;
        
        addListener(new Service.Listener() {
            @Override
            public void running() {
                estimatedBalance.set(wallet().getBalance(Wallet.BalanceType.ESTIMATED).toPlainString());
                try {
                    blocks.set(store().getChainHead().getHeight());
                } catch (BlockStoreException ex) {
                    logger.error("Error getting current chain head while starting wallet "
                            + params.getId(), ex);
                }
                encrypted.set(wallet().isEncrypted());
                controller.registerWallet(Network.this, wallet());
            }

            @Override
            public void failed(Service.State from, Throwable failure) {
                controller.onNetworkFailed(Network.this, from, failure);
            }
        }, networkExecutor);
    }

    @Override
    protected void onSetupCompleted() {
        peerGroup().setConnectTimeoutMillis(1000);
        peerGroup().addDataEventListener(eventBridge);
        peerGroup().addConnectionEventListener(eventBridge);
        chain().addNewBestBlockListener(eventBridge);
        wallet().addCoinEventListener(eventBridge);
    }

    public StringProperty getEstimatedBalanceProperty() {
        return estimatedBalance;
    }

    public IntegerProperty getBlocksProperty() {
        return blocks;
    }

    public IntegerProperty getBlocksLeftProperty() {
        return blocksLeft;
    }

    public BooleanProperty getEncryptedStateProperty() {
        return encrypted;
    }

    public IntegerProperty getPeerCountProperty() {
        return peerCount;
    }

    public NetworkParameters getParams() {
        return params;
    }

    /**
     * Format a coin amount.
     */
    public CharSequence format(Coin amount) {
        return params.getMonetaryFormat().format(amount);
    }

    /**
     * Queue a request to decrypt this wallet. This returns immediately, as the
     * actual work is done on the network thread in order to ensure the thread
     * context is correct. Unhandled errors are reported back to Network.
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
            final long timeout, final TimeUnit timeUnit) {
        this.networkExecutor.execute((Runnable) () -> {
            final Wallet wallet = wallet();
            if (!wallet.isEncrypted()) {
                onCrypterError.accept(null);
            } else {
                final KeyCrypter keyCrypter = wallet().getKeyCrypter();

                if (keyCrypter == null) {
                    throw new IllegalStateException("Wallet is encrypted but has no key crypter.");
                } else {
                    try {
                        wallet().decrypt(keyCrypter.deriveKey(password));
                        encrypted.set(false);
                        onSuccess.accept(null);
                    } catch (KeyCrypterException ex) {
                        onCrypterError.accept(ex);
                    }
                }
            }
        });
    }

    /**
     * Queue a request to encrypt this wallet. This returns immediately, as the
     * actual work is done on the network thread in order to ensure the thread
     * context is correct.
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
            final long timeout, final TimeUnit timeUnit) {
        this.networkExecutor.execute((Runnable) () -> {
            final Wallet wallet = wallet();
            if (wallet.isEncrypted()) {
                onWalletEncrypted.accept(null);
            } else {
                KeyCrypter keyCrypter = wallet().getKeyCrypter();

                if (keyCrypter == null) {
                    keyCrypter = new KeyCrypterScrypt();
                }

                try {
                    wallet().encrypt(keyCrypter, keyCrypter.deriveKey(password));
                    encrypted.set(true);
                    onSuccess.accept(null);
                } catch (KeyCrypterException ex) {
                    onCrypterError.accept(ex);
                }
            }
        });
    }

    /**
     * Queue a request to send coins to the given address. This returns
     * immediately, as the actual work is done on the network thread in order to
     * ensure the thread context is correct.
     *
     * @param req the send coin request to pass to the wallet
     * @param onSuccess handler to be called on success
     * @param onInsufficientFunds handler to be called if the user lacks
     * sufficient funds
     * @param onWalletLocked handler to be called if the wallet is locked and
     * no suitable key is provided in the send request
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void sendCoins(final Wallet.SendRequest req,
            final Consumer<SendResult> onSuccess,
            final Consumer<Coin> onInsufficientFunds,
            final Consumer<KeyCrypterException> onWalletLocked,
            final long timeout, final TimeUnit timeUnit) {
        this.networkExecutor.execute((Runnable) () -> {
            // TODO: Calculate fees in a network-appropriate way
            final SendResult result;
            try {
                result = Network.this.wallet().sendCoins(req);
                onSuccess.accept(result);
            } catch (InsufficientMoneyException ex) {
                onInsufficientFunds.accept(ex.missing);
            } catch (KeyCrypterException ex) {
                onWalletLocked.accept(ex);
            } finally {
                // Wipe the key to ensure if it's stored in insecure memory, it's all
                // zeroes on disk
                if (null != req.aesKey) {
                    Arrays.fill(req.aesKey.getKey(), (byte) 0);
                }
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
        final KeyCrypter keyCrypter = wallet().getKeyCrypter();
        if (keyCrypter != null) {
            return keyCrypter.deriveKey(password);
        } else {
            throw new IllegalStateException("Wallet does not have a key crypter.");
        }
    }

    public class EventBridge implements NewBestBlockListener, PeerDataEventListener, PeerConnectionEventListener, WalletEventListener {

        private EventBridge() {

        }

        @Override
        public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
            Network.this.blocksLeft.set(blocksLeft);
        }

        @Override
        public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            Network.this.blocksLeft.set(blocksLeft);
        }

        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            Network.this.blocks.set(block.getHeight());
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
            Network.this.peerCount.set(peerCount);
        }

        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            Network.this.peerCount.set(peerCount);
        }

        @Override
        public void onCoinsReceived(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
            if (seenTransactions.add(tx)) {
                controller.addTransaction(Network.this, tx, prevBalance, newBalance);
            }
            estimatedBalance.set(wallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString());
        }

        @Override
        public void onCoinsSent(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
            if (seenTransactions.add(tx)) {
                controller.addTransaction(Network.this, tx, prevBalance, newBalance);
            }
            estimatedBalance.set(wallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString());
            // TODO: Update the displayed receive address
        }

        @Override
        public void onReorganize(Wallet wallet) {
            estimatedBalance.set(wallet.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString());
            controller.refreshTransactions(Network.this, wallet);
        }

        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            estimatedBalance.set(wallet.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString());
        }

        @Override
        public void onWalletChanged(Wallet wallet) {
            estimatedBalance.set(wallet.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString());
            controller.refreshTransactions(Network.this, wallet);
        }

        @Override
        public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
            // Don't care
        }

        @Override
        public void onKeysAdded(List<ECKey> keys) {
            // Don't care
        }
    }
}
