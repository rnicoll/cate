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

import com.google.common.util.concurrent.Service;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.util.Callback;
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
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.store.BlockStoreException;
import org.libdohj.cate.controller.MainController;

/**
 * Class which manages incoming events and knows which network they apply
 * to.
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

    /** Work queue for the thread once the kit has been started */
    private final ArrayBlockingQueue<AbstractWork> workQueue = new ArrayBlockingQueue<>(WORK_QUEUE_SIZE);
    private boolean cancelled = false;

    private final SimpleObjectProperty<String> balance = new SimpleObjectProperty<>("0");
    private final SimpleIntegerProperty blocks = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty blocksLeft = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty peerCount = new SimpleIntegerProperty(0);

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
        // TODO: Don't register coins that send to a change address, twice
        controller.addTransaction(params, tx, prevBalance, newBalance);
        balance.set(newBalance.toPlainString());
    }

    @Override
    public void onCoinsSent(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        // TODO: Don't register coins that send to a change address, twice
        controller.addTransaction(params, tx, prevBalance, newBalance);
        balance.set(newBalance.toPlainString());
        // TODO: Update the displayed receive address
    }

    /**
     * @return the wallet kit
     */
    public WalletAppKit getKit() {
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

    public ObservableValue<Number> getObservablePeerCount() {
        return peerCount;
    }

    public NetworkParameters getParams() {
        return params;
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
                Network.this.workQueue.offer(new RegisterWallet());
            }
        };
        kit.setAutoStop(false);
        kit.setBlockingStartup(false);
        kit.startAsync();

        while (!cancelled) {
            final AbstractWork work;
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
     * Queue a request to send coins to the given address. This returns immediately,
     * as the actual work is done on the network thread in order to ensure the
     * thread context is correct.
     * 
     * @param address the recipient address to send coins to.
     * @param amount the amount of coins to send.
     * @param onSuccess handler to be called on success
     * @param onInsufficientFunds handler to be called if the user lacks sufficient funds
     * @param timeout timeout on queueing the work request
     * @param timeUnit time unit for the timeout
     */
    public void sendCoins(final Address address, final Coin amount,
                          final SendSuccessHandler onSuccess,
                          final SendInsufficientFundsHandler onInsufficientFunds,
                          final long timeout, final TimeUnit timeUnit)
            throws InterruptedException {
        this.workQueue.offer(new SendCoins(address, amount, onSuccess, onInsufficientFunds),
            timeout, timeUnit);
    }

    /**
     * Interface for handling the case where the user tries to send funds without
     * enough in the wallet to cover it.
     */
    public interface SendInsufficientFundsHandler {
        /**
         * Called when the user tries to send funds without enough in the wallet
         * to cover it. Will be called on the network thread.
         */
        public void onInsufficientFunds(Coin missing);
    }

    public interface SendSuccessHandler {
        /**
         * Called when a send transaction is generated successfully.
         * Will be called on the network thread.
         */
        public void onSuccess(SendResult sendResult);
    }

    private abstract class AbstractWork implements Runnable {
        
    }

    private class RegisterWallet extends AbstractWork {
        @Override
        public void run() {
            controller.registerWallet(Network.this, Network.this.getKit().wallet());
            try {
                blocks.set(Network.this.getKit().store().getChainHead().getHeight());
            } catch (BlockStoreException ex) {
                // TODO: Log
                System.err.println(ex.toString());
                ex.printStackTrace();
            }
            balance.set(Network.this.getKit().wallet().getBalance().toPlainString());
        }
    }

    private class SendCoins extends AbstractWork {
        private final SendSuccessHandler onSuccess;
        private final SendInsufficientFundsHandler onInsufficientFunds;
        private final Address address;
        private final Coin amount;

        private SendCoins(final Address address, final Coin amount,
                          final SendSuccessHandler onSuccess,
                          final SendInsufficientFundsHandler onInsufficientFunds) {
            this.address = address;
            this.amount = amount;
            this.onSuccess = onSuccess;
            this.onInsufficientFunds = onInsufficientFunds;
        }

        @Override
        public void run() {
            // TODO: Calculate fees in a network-appropriate way
            final Wallet.SendRequest req = Wallet.SendRequest.to(address, amount);
            final SendResult result;
            try {
                result = Network.this.getKit().wallet().sendCoins(req);
            } catch (InsufficientMoneyException ex) {
                onInsufficientFunds.onInsufficientFunds(ex.missing);
                return;
            }
            onSuccess.onSuccess(result);
        }
    }
}
