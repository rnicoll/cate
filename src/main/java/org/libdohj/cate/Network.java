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
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
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
    private final NetworkParameters params;
    private WalletAppKit kit;
    private boolean synced = false;
    private final MainController controller;
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
        controller.addTransaction(params, tx, prevBalance, newBalance);
        balance.set(newBalance.toPlainString());
    }

    @Override
    public void onCoinsSent(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        controller.addTransaction(params, tx, prevBalance, newBalance);
        balance.set(newBalance.toPlainString());
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
                controller.registerWallet(wallet());
                try {
                    blocks.set(store().getChainHead().getHeight());
                } catch (BlockStoreException ex) {
                    // TODO: Log
                    System.err.println(ex.toString());
                    ex.printStackTrace();
                }
                balance.set(wallet().getBalance().toPlainString());
            }
        };
        kit.setAutoStop(false);
        kit.setBlockingStartup(false);
        kit.startAsync();
    }
}
