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
package org.libdohj.cate.controller;

import java.io.File;
import java.text.DateFormat;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import com.google.common.util.concurrent.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.libdohj.cate.util.NetworkResolver;
import org.spongycastle.crypto.params.KeyParameter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import org.libdohj.cate.Network;
import org.libdohj.cate.util.NetworkThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base window from which the rest of CATE is launched. Lists any active
 * wallets, their status, and options to add new wallets.
 *
 * @author Ross Nicoll
 */
public class MainController {

    private static final int NETWORK_PUSH_TIMEOUT_MILLIS = 500;

    @FXML // ResourceBundle that was given to the FXMLLoader
    private ResourceBundle resources;

    @FXML
    private MenuItem menuExit;

    @FXML
    private ComboBox<Wallet> receiveSelector;
    @FXML
    private TextField myAddress;
    @FXML
    private TableView txList;
    @FXML
    private TableColumn<WalletTransaction, String> txNetworkColumn;
    @FXML
    private TableColumn<WalletTransaction, String> txDateColumn;
    @FXML
    private TableColumn<WalletTransaction, String> txAmountColumn;
    @FXML
    private TableColumn<WalletTransaction, String> txLabelColumn;
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
    private final Map<Wallet, Network> walletNetworks = new HashMap<>();
    private final Map<Network, ExecutorService> executors = new HashMap<>();
    private KeyCrypterScrypt keyCrypter;

    private final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    public void initialize() {
        receiveSelector.setItems(wallets);
        sendSelector.setItems(wallets);
        receiveSelector.setConverter(new WalletToNetworkNameConvertor());
        sendSelector.setConverter(receiveSelector.getConverter());

        initializeWalletList();
        initializeTransactionList();

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
            sendCoinsOnUIThread(event);
        });

        menuExit.setOnAction((ActionEvent event) -> {
            Platform.exit();
        });
    }

    /**
     * Connect to the specified network.
     *
     * @param params network parameters for the relay network to connect to.
     * @param dataDir directory to store data files in.
     * @return the service that has been started. Can be used to test start
     * completes successfully.
     */
    public Service connectTo(final NetworkParameters params, final File dataDir) {
        final Context context = new Context(params);
        final NetworkThreadFactory threadFactory = new NetworkThreadFactory(context);
        final ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
        final Network network = new Network(context, this, dataDir, executor);
        threadFactory.setUncaughtExceptionHandler(buildUncaughtExceptionHandler(network));
        networks.add(network);

        // Add a listener to shut down the executor service once the network service
        // it's responsible for terminates.
        network.addListener(new Service.Listener() {
            @Override
            public void terminated(Service.State from) {
                executor.shutdown();
            }
        }, executor);

        // Record the executor in case we want to do anything with it later.
        executors.put(network, executor);

        final Service service = network.startAsync();
        return service;
    }

    private void initializeTransactionList() {
        txList.setItems(transactions);
        txNetworkColumn.setCellValueFactory(dataFeatures -> {
            final WalletTransaction transaction = dataFeatures.getValue();
            final NetworkParameters params = transaction.getParams();
            return new SimpleStringProperty(NetworkResolver.getName(params));
        });
        txDateColumn.setCellValueFactory(dataFeatures -> {
            final WalletTransaction transaction = dataFeatures.getValue();
            final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            return new SimpleStringProperty(dateFormat.format(transaction.getTransaction().getUpdateTime()));
        });
        txAmountColumn.setCellValueFactory(dataFeatures -> {
            final WalletTransaction transaction = dataFeatures.getValue();
            return new SimpleStringProperty(transaction.getBalanceChange().toPlainString());
        });
    }

    private void initializeWalletList() {
        walletList.setItems(networks);
        walletList.setRowFactory(view -> {
            final TableRow<Network> row = new TableRow<>();
            final ContextMenu rowMenu = new ContextMenu();
            final MenuItem encryptItem = new MenuItem(resources.getString("menuItem.encrypt"));
            final MenuItem decryptItem = new MenuItem(resources.getString("menuItem.decrypt"));

            // TODO: Enable/disable options based on whether the wallet is locked.
            // Alternatively have two different context menus that display different
            // options.
            encryptItem.setOnAction(action -> encryptWalletOnUIThread(row.getItem()));
            decryptItem.setOnAction(action -> decryptWalletOnUIThread(row.getItem()));

            rowMenu.getItems().addAll(encryptItem, decryptItem);

            row.contextMenuProperty().set(rowMenu);

            return row;
        });
        networkName.setCellValueFactory(dataFeatures -> {
            final Network network = dataFeatures.getValue();
            final NetworkParameters params = network.getParams();
            return new SimpleStringProperty(NetworkResolver.getName(params));
        });
        networkBalance.setCellValueFactory(dataFeatures -> {
            final Network network = dataFeatures.getValue();
            return network.getObservableBalance();
        });
        networkPeers.setCellValueFactory(dataFeatures -> {
            final Network network = dataFeatures.getValue();
            return network.getObservablePeerCount();
        });
        networkBlocks.setCellValueFactory(dataFeatures -> {
            final Network network = dataFeatures.getValue();
            return network.getObservableBlocks();
        });
    }

    /**
     * Add a transaction to those displayed by this controller.
     *
     * @param params network parameters for the network the transaction is from.
     * @param tx the underlying transaction to add.
     * @param prevBalance previous wallet balance.
     * @param newBalance new wallet balance.
     */
    public void addTransaction(NetworkParameters params, Transaction tx, Coin prevBalance, Coin newBalance) {
        // TODO: Transaction lists should be aggregated from listed held by each
        // network.
        // For now we do the actual modification on the UI thread to avoid
        // race conditions
        Platform.runLater(() -> {
            transactions.add(0, new WalletTransaction(params, tx, newBalance.subtract(prevBalance)));
        });
    }

    /**
     * Prompts the user for the wallet password and then decrypts the underlying
     * wallet. Shows a warning then takes no further action if the wallet is not
     * encrypted.
     */
    private void decryptWalletOnUIThread(final Network network) {
        if (!network.getObservableEncryptedState().getValue()) {
            Alert alert = new Alert(Alert.AlertType.ERROR,resources.getString("alert.walletUnencrypted.msg"));
            alert.setTitle(resources.getString("alert.walletUnencrypted.title"));
            alert.showAndWait();
            return;
        }

        PasswordInputDialog passwordDialog = new PasswordInputDialog();
        passwordDialog.setTitle(resources.getString("dialogDecrypt.title"));
        passwordDialog.setHeaderText(resources.getString("dialogDecrypt.msg"));
        passwordDialog.setContentText(resources.getString("dialogDecrypt.label"));

        passwordDialog.showAndWait().ifPresent(value -> {
            network.decrypt(value, o -> {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(resources.getString("alert.decryptWallet.successTitle"));
                    alert.setContentText(resources.getString("alert.decryptWallet.successMsg"));
                    alert.showAndWait();
                });
            }, t -> {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING,
                            resources.getString("alert.decryptWallet.noticeMsg"));
                    alert.setTitle(resources.getString("alert.decryptWallet.noticeTitle"));
                    alert.showAndWait();
                });
            }, t -> {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR,
                            t.getMessage());
                    alert.setTitle(resources.getString("alert.decryptWallet.errorTitle"));
                    alert.showAndWait();
                });
            }, NETWORK_PUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Prompts the user for the wallet password and then encrypts the underlying
     * wallet. If the wallet is already encrypted it changes the encryption key.
     */
    private void encryptWalletOnUIThread(final Network network) {
        if (network.getObservableEncryptedState().getValue()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, resources.getString("alert.walletEncrypted.msg"));
            alert.setTitle(resources.getString("alert.walletEncrypted.title"));
            alert.showAndWait();
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(resources.getString("dialogEncrypt.title"));
        dialog.setHeaderText(resources.getString("dialogEncrypt.msg"));

        PasswordField pass1 = new PasswordField();
        PasswordField pass2 = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label(resources.getString("dialogEncrypt.passNew")), 0, 0);
        grid.add(pass1, 1, 0);
        grid.add(new Label(resources.getString("dialogEncrypt.passRepeat")), 0, 1);
        grid.add(pass2, 1, 1);

        ButtonType buttonTypeOk = new ButtonType(resources.getString("buttonType.Ok"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(buttonTypeOk, ButtonType.CANCEL);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(pass1::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == buttonTypeOk) {
                if (!pass1.getText().trim().isEmpty() && !pass2.getText().trim().isEmpty()) {
                    if (Objects.equals(pass1.getText(), pass2.getText())) {
                        return pass1.getText();
                    } else {
                        return resources.getString("responseType.False");
                    }
                } else {
                    return resources.getString("responseType.False");
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(value -> {
            if (Objects.equals(value, resources.getString("responseType.False"))) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION,
                            resources.getString("alert.encryptWallet.mismatchMsg"));
                    alert.setTitle(resources.getString("alert.encryptWallet.errorTitle"));
                    alert.showAndWait();
                });
            } else {
                network.encrypt(value, o -> {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                resources.getString("alert.encryptWallet.successMsg"));
                        alert.setTitle(resources.getString("alert.encryptWallet.successTitle"));
                        alert.showAndWait();
                    });
                }, t -> {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING,
                                resources.getString("alert.encryptWallet.noticeMsg"));
                        alert.setTitle(resources.getString("alert.encryptWallet.noticeTitle"));
                        alert.showAndWait();
                    });
                }, t -> {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR,
                                t.getMessage());
                        alert.setTitle(resources.getString("alert.encryptWallet.errorTitle"));
                        alert.showAndWait();
                    });
                }, NETWORK_PUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Take coin values to send from the user interface, prompt the user to
     * confirm, and then send the coins. MUST be called on the UI thread.
     */
    private void sendCoinsOnUIThread(ActionEvent event) {
        final Address address;
        final Coin amount;
        final Network network = walletNetworks.get(sendSelector.getValue());

        try {
            address = Address.fromBase58(network.getParams(), sendAddress.getText());
        } catch (AddressFormatException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, resources.getString("sendCoins.addressError.msg")
                    + ex.getMessage());
            alert.setTitle(resources.getString("sendCoins.addressError.title"));
            alert.showAndWait();
            return;
        }

        try {
            amount = Coin.parseCoin(sendAmount.getText());
        } catch (IllegalArgumentException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, resources.getString("sendCoins.amountError.msg")
                    + ex.getMessage());
            alert.setTitle(resources.getString("sendCoins.amountError.title"));
            alert.showAndWait();
            return;
        }

        final TransactionConfirmationAlert confirmSend = new TransactionConfirmationAlert(network.getParams());

        // TODO: Show details of fees and total including fees
        confirmSend.setTitle(resources.getString("sendCoins.confirm.title"));
        confirmSend.setAddressLabel(resources.getString("sendCoins.confirm.address"));
        confirmSend.setAmountLabel(resources.getString("sendCoins.confirm.amount"));
        confirmSend.setAddress(address);
        confirmSend.setAmount(amount);
        confirmSend.initOwner(((Node) event.getTarget()).getScene().getWindow());

        confirmSend.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> doSendCoins(network, address, amount));
    }

    /**
     * Actually send coins, once the user has confirmed.
     *
     * @param network the network to send coins over.
     * @param address the recipient address to send coins to.
     * @param amount the amount of coins to send.
     */
    private void doSendCoins(final Network network, final Address address, final Coin amount) {
        final Wallet.SendRequest req = Wallet.SendRequest.to(address, amount);

        // Prompt for password if the wallet is encrypted
        // TODO: Should have an unlock() method we call here,
        // and uses cached password for ~5 minutes, rather than prompting every
        // time
        if (network.getObservableEncryptedState().getValue()) {
            req.aesKey = getAESKeyFromUser(network);
            if (req.aesKey == null) {
                // No key available, which means the user hit cancel
                return;
            }
        }

        network.sendCoins(req,
                (Wallet.SendResult sendResult) -> {
                    // TODO: Can we do a "toast" pop up of some kind here?
                }, (Coin missing) -> {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle(resources.getString("doSendCoins.moneyError.title"));
                        alert.setHeaderText(resources.getString("doSendCoins.moneyError.head"));
                        alert.setContentText(resources.getString("doSendCoins.moneyError.msg1")
                                + (missing == null
                                        ? resources.getString("doSendCoins.moneyError.msg2")
                                        : network.format(missing))
                                + resources.getString("doSendCoins.moneyError.msg3"));

                        alert.showAndWait();
                    });
                }, (KeyCrypterException ex) -> {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle(resources.getString("doSendCoins.walletLocked.title"));
                        alert.setHeaderText(resources.getString("doSendCoins.walletLocked.head"));
                        alert.setContentText(resources.getString("doSendCoins.walletLocked.msg"));
                        alert.showAndWait();
                    });
                }, NETWORK_PUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private KeyParameter getAESKeyFromUser(final Network network) {
        // TODO: Cache the key for ~5 minutes

        // I don't like that we have to hold the password as a string, so we
        // can't wipe the values once we're done.
        final PasswordInputDialog passwordDialog = new PasswordInputDialog();
        passwordDialog.setContentText(resources.getString("getAESKey.msg"));

        // We don't use lambdas here because we're returning a value based on
        // the evaluation
        final Optional<String> password = passwordDialog.showAndWait();
        if (password.isPresent()) {
            return network.getKeyFromPassword(password.get());
        } else {
            return null;
        }
    }

    /**
     * Register a wallet to be tracked by this controller. This recalculates
     * wallet transactions, which is a long running task, and must be run on a
     * background thread.
     */
    public void registerWallet(final Network network, final Wallet wallet) {
        // We rebuild the transactions on the current thread, rather than slowing
        // down the UI thread, and so keep a temporary copy to be pushed into the
        // main transaction list later.
        final List<WalletTransaction> tempTransactions = rebuildTransactions(wallet);

        Collections.reverse(tempTransactions);
        Platform.runLater(() -> {
            this.wallets.add(wallet);
            if (this.wallets.size() == 1) {
                // We've just added the first wallet, choose it
                this.receiveSelector.setValue(wallet);
                this.sendSelector.setValue(wallet);
            }

            // TODO: Need to enforce order of transactions by time, not by
            // network and then time as this does
            transactions.addAll(tempTransactions);
        });

        walletNetworks.put(wallet, network);
    }

    /**
     * Stops the controller, which includes shutting down the various networks
     * it is managing.
     *
     * @return a list of services which have been notified to stop.
     */
    public List<Service> stop() {
        return networks.stream()
                .map(network -> network.stopAsync())
                .collect(Collectors.toList());
    }

    /**
     * Builds an uncaught exception handler for threads belonging to a relay
     * network.
     *
     * @param network network the handler is for.
     * @return an uncaught exception handler.
     */
    public Thread.UncaughtExceptionHandler buildUncaughtExceptionHandler(final Network network) {
        return (Thread thread, Throwable thrwbl) -> {
            if (thrwbl instanceof Exception) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(resources.getString("internalError.title"));
                    alert.setContentText(thrwbl.getMessage());
                    alert.showAndWait();
                    // TODO: Shut down and de-register the wallet from currently
                    // running
                });
            } else {
                // Fatal, begin shutdown
                Platform.exit();
            }
        };
    }

    /**
     * Handle a network service failing.
     *
     * @param network the service which failed.
     * @param from the status the service was in before it failed.
     * @param thrwbl the exception causing the service to fail.
     */
    public void onNetworkFailed(Network network, Service.State from, Throwable thrwbl) {
        networks.remove(network);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(resources.getString("internalError.title"));
            alert.setContentText(thrwbl.getMessage());
            alert.showAndWait();
        });
    }

    public void refreshTransactions(Network network, Wallet wallet) {
        // TODO: Clear transactions from the given wallet out of the list
        // then re-apply them
    }

    private List<WalletTransaction> rebuildTransactions(final Wallet wallet) {
        // We rebuild the transactions on the current thread, rather than slowing
        // down the UI thread, and so keep a temporary copy to be pushed into the
        // main transaction list later.
        final List<WalletTransaction> tempTransactions = new ArrayList<>();

        // Pre-sort transactions by date
        final SortedSet<Transaction> rawTransactions = new TreeSet<>(
                (Transaction a, Transaction b) -> a.getUpdateTime().compareTo(b.getUpdateTime())
        );
        rawTransactions.addAll(wallet.getTransactions(false));

        final Map<TransactionOutPoint, Coin> balances = new HashMap<>();
        // TODO: Should get the value change or information on relevant outputs
        // or something more useful form Wallet
        // Meanwhile we do a bunch of duplicate work to recalculate these values,
        // here
        for (Transaction tx : rawTransactions) {
            long valueChange = 0;
            for (TransactionInput in : tx.getInputs()) {
                Coin balance = balances.get(in.getOutpoint());
                // Spend the value on the listed input
                if (balance != null) {
                    valueChange -= balance.value;
                    balances.remove(in.getOutpoint());
                }
            }
            for (TransactionOutput out : tx.getOutputs()) {
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
            tempTransactions.add(new WalletTransaction(wallet.getParams(), tx, Coin.valueOf(valueChange)));
        }

        Collections.reverse(tempTransactions);
        return tempTransactions;
    }

    private class WalletToNetworkNameConvertor extends StringConverter<Wallet> {

        public WalletToNetworkNameConvertor() {
        }

        @Override
        public String toString(Wallet wallet) {
            return NetworkResolver.getName(wallet.getParams());
        }

        @Override
        public Wallet fromString(String string) {
            final NetworkParameters params = NetworkResolver.getParameter(string);
            for (Wallet wallet : wallets) {
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
