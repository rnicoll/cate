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

import org.libdohj.cate.Network;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javafx.beans.property.SimpleObjectProperty;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
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
    private TableColumn<WalletTransaction, String> txNetworkColumn;
    @FXML
    private TableColumn<WalletTransaction, Date> txDateColumn;
    @FXML
    private TableColumn<WalletTransaction, String> txTypeColumn;
    @FXML
    private TableColumn<WalletTransaction, String> txAmountColumn;
    @FXML
    private TableColumn<WalletTransaction, String> txStateColumn;
    @FXML
    private TextField sendAddress;
    @FXML
    private TextField sendAmount;
    @FXML
    private ComboBox<Wallet> sendSelector;
    @FXML
    private Button sendButton;
    @FXML
    private TableView<Network> walletList;
    @FXML
    private TableColumn<Network, String> networkName;
    @FXML
    private TableColumn<Network, String> networkBalance;
    @FXML
    private TableColumn<Network, Number> networkBlocks;
    @FXML
    private TableColumn<Network, Number> networkPeers;

    private final ObservableList<Network> networks = FXCollections.observableArrayList();
    private final ObservableList<Wallet> wallets = FXCollections.observableArrayList();
    private final ObservableList<WalletTransaction> transactions = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        receiveSelector.setItems(wallets);
        sendSelector.setItems(wallets);
        txList.setItems(transactions);
        walletList.setItems(networks);

        // Set up wallet list rendering
        networkName.setCellValueFactory((TableColumn.CellDataFeatures<Network, String> param) -> {
            final Network network = param.getValue();
            final NetworkParameters params = network.getParams();
            return new SimpleStringProperty(getNetworkName(params));
        });
        networkBalance.setCellValueFactory((TableColumn.CellDataFeatures<Network, String> param) -> {
            final Network network = param.getValue();
            return network.getObservableBalance();
        });
        networkPeers.setCellValueFactory((TableColumn.CellDataFeatures<Network, Number> param) -> {
            final Network network = param.getValue();
            return network.getObservablePeerCount();
        });
        networkBlocks.setCellValueFactory((TableColumn.CellDataFeatures<Network, Number> param) -> {
            final Network network = param.getValue();
            return network.getObservableBlocks();
        });

        // Set up transaction column rendering
        txNetworkColumn.setCellValueFactory((TableColumn.CellDataFeatures<WalletTransaction, String> param) -> {
            final WalletTransaction transaction = param.getValue();
            final NetworkParameters params = transaction.getParams();
            return new SimpleStringProperty(getNetworkName(params));
        });
        txDateColumn.setCellValueFactory((TableColumn.CellDataFeatures<WalletTransaction, Date> param) -> {
            final WalletTransaction transaction = param.getValue();
            return new SimpleObjectProperty(transaction.getTransaction().getUpdateTime());
        });
        txAmountColumn.setCellValueFactory((TableColumn.CellDataFeatures<WalletTransaction, String> param) -> {
            final WalletTransaction transaction = param.getValue();
            return new SimpleStringProperty(transaction.getBalanceChange().toPlainString());
        });

        receiveSelector.setConverter(new WalletToNetworkNameConvertor());
        sendSelector.setConverter(receiveSelector.getConverter());

        NetworkParameters bitcoinParams = networksByName.get("Bitcoin");
        NetworkParameters bitcoinTestParams = networksByName.get("Bitcoin test");
        NetworkParameters dogecoinParams = networksByName.get("Dogecoin");
        NetworkParameters dogecoinTestParams = networksByName.get("Dogecoin test");

        /* newWallet(bitcoinParams);
        newWallet(bitcoinTestParams); */
        networks.add(new Network(dogecoinParams, this));
        networks.add(new Network(dogecoinTestParams, this));

        for (Network network: networks) {
            network.run();
        }

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
            final Coin amount;
            final Wallet wallet = sendSelector.getValue();

            try {
                address = Address.fromBase58(wallet.getNetworkParameters(), sendAddress.getText());
            } catch(AddressFormatException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "The provided address is invalid: "
                    + ex.getMessage());
                alert.setTitle("Address Incorrect");
                alert.showAndWait();
                return;
            }

            try {
                amount = Coin.parseCoin(sendAmount.getText());
            } catch(IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "The number of coins to send is invalid: "
                    + ex.getMessage());
                alert.setTitle("Amount Incorrect");
                alert.showAndWait();
                return;
            }

            // TODO: Calculate fees in a network-appropriate way
            SendRequest req = SendRequest.to(address, amount);
            try {
                // Wondering if we should have all wallet interaction on a per-network
                // thread, and therefore this should be going via the network bridge.
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

        for (Network bridge: networks) {
            services.add(bridge.getKit().stopAsync());
            try {
                bridge.join();
            } catch(InterruptedException ex) {
                // TODO: Log
                // Ignore and move on to the next thread
            }
        }

        return services;
    }

    public void addTransaction(NetworkParameters params, Transaction tx, Coin prevBalance, Coin newBalance) {
        // TODO: Transaction lists should be aggregated from listed held by each
        // network.
        // For now we do the actual modification on the UI thread to avoid
        // race conditions
        Platform.runLater(() -> {
            transactions.add(new WalletTransaction(params, tx, newBalance.subtract(prevBalance)));
        });
    }

    /**
     * @return the name of a network
     */
    public static String getNetworkName(final NetworkParameters params) {
        return networkNames.get(params);
    }

    /**
     * Register a wallet to be tracked by this controller.
     */
    public void registerWallet(Wallet wallet) {
        Platform.runLater(() -> {
            this.wallets.add(wallet);
            if (this.wallets.size() == 1) {
                // We've just added the first wallet, choose it
                this.receiveSelector.setValue(wallet);
                this.sendSelector.setValue(wallet);
            }

            // Pre-sort transactions by date
            final SortedSet<Transaction> rawTransactions = new TreeSet<Transaction>(
                (Transaction a, Transaction b) -> a.getUpdateTime().compareTo(b.getUpdateTime())
            );
            rawTransactions.addAll(wallet.getTransactions(false));

            final Map<TransactionOutPoint, Coin> balances = new HashMap<>();
            // TODO: Should get the value change or information on relevant outputs
            // or something more useful form Wallet
            // Meanwhile we do a bunch of duplicate work to recalculate these values,
            // here
            for (Transaction tx: rawTransactions) {
                long valueChange = 0;
                for (TransactionInput in: tx.getInputs()) {
                    Coin balance = balances.get(in.getOutpoint());
                    // Spend the value on the listed input
                    if (balance != null) {
                        valueChange -= balance.value;
                        balances.remove(in.getOutpoint());
                    }
                }
                for (TransactionOutput out: tx.getOutputs()) {
                    if (out.isMine(wallet)) {
                        valueChange += out.getValue().value;
                        Coin balance = balances.get(out.getOutPointFor());
                        if (balance == null) {
                            balance = out.getValue();
                        } else {
                            balance.add(out.getValue());
                        }
                        balances.put(out.getOutPointFor(), balance);
                    }
                }
                transactions.add(new WalletTransaction(wallet.getParams(), tx, Coin.valueOf(valueChange)));
            }
        });
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

    public static class WalletTransaction extends Object {
        private final NetworkParameters params;
        private final Transaction transaction;
        private final Coin balanceChange;

        private WalletTransaction(final NetworkParameters params,
            final Transaction transaction, final Coin balanceChange) {
            this.params = params;
            this.transaction = transaction;
            this.balanceChange = balanceChange;
        }

        /**
         * @return the network params
         */
        public NetworkParameters getParams() {
            return params;
        }

        /**
         * @return the transaction
         */
        public Transaction getTransaction() {
            return transaction;
        }

        /**
         * @return the balanceChange
         */
        public Coin getBalanceChange() {
            return balanceChange;
        }
    }
}
