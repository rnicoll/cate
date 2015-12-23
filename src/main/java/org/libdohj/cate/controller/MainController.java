/*
 * Copyright 2015 Ross Nicoll.
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
package org.libdohj.cate.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import com.google.common.util.concurrent.Service;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
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
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.core.listeners.PeerConnectionEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.WalletCoinEventListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.cate.cellfactories.WalletBalanceCellFactory;
import org.libdohj.cate.cellfactories.WalletNetworkCellFactory;
import org.libdohj.params.DogecoinMainNetParams;
import org.libdohj.params.DogecoinTestNet3Params;
import org.libdohj.params.LitecoinMainNetParams;

/**
 * Base window from which the rest of CATE is launched. Lists any active
 * wallets, their status, and options to add new wallets.
 * 
 * @author Ross Nicoll
 */
public class MainController {
    private static final LinkedHashMap<NetworkParameters, String> networkNames = new LinkedHashMap<>();
    private static final LinkedHashMap<String, NetworkParameters> networksByName = new LinkedHashMap<>();

    static {
        // TODO: Localize
        networkNames.put(MainNetParams.get(), "Bitcoin");
        networkNames.put(TestNet3Params.get(), "Bitcoin test");
        networkNames.put(LitecoinMainNetParams.get(), "Litecoin");
        networkNames.put(DogecoinMainNetParams.get(), "Dogecoin");
        networkNames.put(DogecoinTestNet3Params.get(), "Dogecoin test");

        for (NetworkParameters params: networkNames.keySet()) {
            networksByName.put(networkNames.get(params), params);
        }
    }

    @FXML
    private ComboBox<Wallet> receiveSelector;
    @FXML
    private TextField myAddress;
    @FXML
    private Button copyAddress;
    @FXML
    private TableView txList;
    @FXML
    private TableColumn<Transaction, String> txNetworkColumn;
    @FXML
    private TableColumn<Transaction, Date> txDateColumn;
    @FXML
    private TableColumn<Transaction, String> txTypeColumn;
    @FXML
    private TableColumn<Transaction, Coin> txAmountColumn;
    @FXML
    private TableColumn<Transaction, String> txStateColumn;
    @FXML
    private TextField sendAddress;
    @FXML
    private TextField sendAmount;
    @FXML
    private ComboBox<Wallet> sendSelector;
    @FXML
    private Button sendButton;
    @FXML
    private TableView walletList;
    @FXML
    private TableColumn<Wallet, String> walletNetwork;
    @FXML
    private TableColumn<Wallet, Coin> walletBalance;

    private final ObservableList<NetworkBridge> networks = FXCollections.observableArrayList();
    private final ObservableList<Wallet> wallets = FXCollections.observableArrayList();
    private final ObservableList<Transaction> transactions = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        receiveSelector.setItems(wallets);
        sendSelector.setItems(wallets);
        txList.setItems(transactions);
        walletList.setItems(wallets);

        walletNetwork.setCellValueFactory(new WalletNetworkCellFactory());
        walletBalance.setCellValueFactory(new WalletBalanceCellFactory());
        receiveSelector.setConverter(new WalletToNetworkNameConvertor());
        sendSelector.setConverter(receiveSelector.getConverter());

        NetworkParameters bitcoinParams = networksByName.get("Bitcoin");
        NetworkParameters bitcoinTestParams = networksByName.get("Bitcoin test");
        NetworkParameters dogecoinParams = networksByName.get("Dogecoin");
        NetworkParameters dogecoinTestParams = networksByName.get("Dogecoin test");
        
        newWallet(bitcoinParams);
        newWallet(bitcoinTestParams);
        newWallet(dogecoinParams);
        newWallet(dogecoinTestParams);

        receiveSelector.setOnAction((ActionEvent event) -> {
            if (event.getTarget().equals(receiveSelector)) {
                final Wallet wallet = (Wallet) receiveSelector.getValue();
                final Address address = wallet.currentReceiveAddress();
                Platform.runLater(() -> {
                    myAddress.setText(address.toBase58());
                });
            }
        });

        sendButton.setOnAction((ActionEvent event) -> {
            final Address address;
            final Wallet wallet = sendSelector.getValue();

            try {
                address = Address.fromBase58(wallet.getNetworkParameters(), sendAddress.getText());
            } catch(AddressFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Address Incorrect");

                alert.setContentText("The provided address is invalid: "
                    + ex.getMessage());

                alert.showAndWait();
                return;
            }

            // TODO validate!
            SendRequest req = SendRequest.to(address, Coin.parseCoin(sendAmount.getText()));
            try {
                wallet.sendCoins(req);
            } catch (InsufficientMoneyException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Insufficient Money");
                alert.setHeaderText("You don't have enough money!");
                alert.setContentText("You need "
                    + (e.missing == null
                        ? "an unknown amount"
                        : wallet.getParams().getMonetaryFormat().format(e.missing))
                    + " more");

                alert.showAndWait();
                return;
            }
        });
    }

    public List<Service> stop() {
        final List<Service> services = new ArrayList<>(networks.size());

        for (NetworkBridge bridge: networks) {
            services.add(bridge.getKit().stopAsync());
        }

        return services;
    }

    /**
     * Load/create a new wallet and add it to the UI.
     */
    private void newWallet(NetworkParameters params) {
        final NetworkBridge bridge = new NetworkBridge(params);

        final WalletAppKit kit = new WalletAppKit(params, new File("."), "cate_" + params.getId()) {
            @Override
            protected void onSetupCompleted() {
                peerGroup().setConnectTimeoutMillis(1000);

                peerGroup().addDataEventListener(bridge);
                peerGroup().addConnectionEventListener(bridge);
                wallet().addCoinEventListener(bridge);

                Platform.runLater(() -> {
                    MainController.this.wallets.add(wallet());
                    if (MainController.this.wallets.size() == 1) {
                        // We've just added the first wallet, choose it
                        MainController.this.receiveSelector.setValue(wallet());
                        MainController.this.sendSelector.setValue(wallet());
                    }
                    // TODO: Constrain this list?
                    transactions.addAll(wallet().getTransactions(false));
                });
            }
        };
        kit.setAutoStop(false);
        kit.setBlockingStartup(false);

        kit.startAsync();
        bridge.setKit(kit);
        networks.add(bridge);
    }

    /**
     * @return the name of a network
     */
    public static String getNetworkName(final NetworkParameters params) {
        return networkNames.get(params);
    }

    private class WalletToNetworkNameConvertor extends StringConverter<Wallet> {

        public WalletToNetworkNameConvertor() {
        }

        @Override
        public String toString(Wallet wallet) {
            return networkNames.get(wallet.getParams());
        }

        @Override
        public Wallet fromString(String string) {
            final NetworkParameters params = networksByName.get(string);
            for (Wallet wallet: wallets) {
                if (wallet.getParams().equals(params)) {
                    return wallet;
                }
            }
            return null;
        }
    }

    /**
     * Class which manages incoming events and knows which network they apply
     * to.
     */
    private class NetworkBridge implements PeerDataEventListener, PeerConnectionEventListener, WalletCoinEventListener {
        private final NetworkParameters params;
        private WalletAppKit kit;
        private boolean synced = false;

        public NetworkBridge(NetworkParameters params) {
            this.params = params;
        }

        @Override
        public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
            if (blocksLeft <= 0) {
                synced = true;
            }

            Platform.runLater(() -> {
                updateSyncStateOnUIThread(blocksLeft);
            });
        }

        @Override
        public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            Platform.runLater(() -> {
                updateSyncStateOnUIThread(blocksLeft);
            });
        }

        private void updateSyncStateOnUIThread(final int blocksLeft) {
            /* if (synced) {
                status.setText("Synchronized");
            } else {
                status.setText("Syncing");
            }
            if (null != kit) {
                blocks.setText(Integer.toString(kit.chain().getBestChainHeight()));
            }
            blocksRemaining.setText(Integer.toString(blocksLeft)); */
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
            /* Platform.runLater(() -> {
                peers.setText(Integer.toString(peerCount));
            }); */
        }

        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            /* Platform.runLater(() -> {
                peers.setText(Integer.toString(peerCount));
            }); */
        }

        @Override
        public void onCoinsReceived(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
            // TODO: Is this enough information or do we need a wrapper that tracks recv/send
            Platform.runLater(() -> {
                transactions.add(tx);
            });
        }

        @Override
        public void onCoinsSent(Wallet wallet, final Transaction tx, final Coin prevBalance, final Coin newBalance) {
            Platform.runLater(() -> {
                transactions.add(tx);
            });
        }

        /**
         * @return the kit
         */
        public WalletAppKit getKit() {
            return kit;
        }

        /**
         * @param kit the kit to set
         */
        public void setKit(WalletAppKit kit) {
            this.kit = kit;
        }
    }
}
