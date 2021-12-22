/*
 * Copyright 2016 Ross Nicoll.
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.utils.MonetaryFormat;
import org.libdohj.cate.CATE;

import java.util.ResourceBundle;

/**
 * @author Ross Nicoll
 */
public class TransactionConfirmationAlert extends Alert {
    final GridPane grid;
    final Label content = new Label();
    final Label amount = new Label();
    final Label address = new Label();
    final TextField memo = new TextField();
    final Label amountLabel = new Label();
    final Label addressLabel = new Label();
    final Label memoLabel = new Label();
    final ObjectProperty<Address> addressProperty = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> amountProperty = new SimpleObjectProperty<>();
    final MonetaryFormat format;

    public TransactionConfirmationAlert(final NetworkParameters params, final ResourceBundle resources) {
        super(Alert.AlertType.CONFIRMATION);

        setTitle(resources.getString("sendCoins.confirm.title"));
        addressLabel.setText(resources.getString("sendCoins.confirm.address"));
        addressLabel.getStyleClass().add("label-heading");
        amountLabel.setText(resources.getString("sendCoins.confirm.amount"));
        amountLabel.getStyleClass().add("label-heading");
        memoLabel.setText(resources.getString("sendCoins.confirm.memo"));
        memoLabel.getStyleClass().add("label-heading");

        format = params.getMonetaryFormat();
        grid = new GridPane();

        contentTextProperty().addListener((observable, oldVal, newVal) -> {
            content.setText(newVal);
        });
        amountProperty().addListener((observable, oldVal, newVal) -> {
            amount.setText(format.format(newVal).toString());
        });
        addressProperty().addListener((observable, oldVal, newVal) -> {
            if (newVal instanceof LegacyAddress) {
                address.setText(((LegacyAddress)newVal).toBase58());
            } else {
                address.setText(newVal.toString());
            }
        });

        grid.setHgap(MainController.DIALOG_HGAP);
        grid.setVgap(MainController.DIALOG_VGAP);
        int row = 0;
        grid.addRow(row++, content);
        grid.addRow(row++, addressLabel, address);
        grid.addRow(row++, memoLabel, memo);
        grid.addRow(row++, amountLabel, amount);

        getDialogPane().getStylesheets().add(CATE.DEFAULT_STYLESHEET);
        getDialogPane().setContent(grid);
    }

    public final ObjectProperty<Address> addressProperty() {
        return addressProperty;
    }

    public final ObjectProperty<Coin> amountProperty() {
        return amountProperty;
    }

    public final Address getAddress() {
        return addressProperty.get();
    }

    public final Coin getAmount() {
        return amountProperty.get();
    }

    public String getMemo() {
        return memo.getText();
    }

    public final void setAddress(final Address address) {
        this.addressProperty.set(address);
    }

    public final void setAmount(final Coin amount) {
        this.amountProperty.set(amount);
    }

    public void setMemo(String memo) {
        this.memo.setText(memo);
    }
}
