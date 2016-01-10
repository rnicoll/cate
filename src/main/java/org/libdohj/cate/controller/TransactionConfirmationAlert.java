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
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.utils.MonetaryFormat;

/**
 * @author Ross Nicoll
 */
public class TransactionConfirmationAlert extends Alert {
    final GridPane grid;
    final Label content = new Label();
    final Label amount = new Label("Amount:");
    final Label address = new Label("Address:");
    final Label amountLabel = new Label();
    final Label addressLabel = new Label();
    final ObjectProperty<Address> addressProperty = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> amountProperty = new SimpleObjectProperty<>();
    final MonetaryFormat format;

    public TransactionConfirmationAlert(final NetworkParameters params) {
        super(Alert.AlertType.CONFIRMATION);

        format = params.getMonetaryFormat();
        grid = new GridPane();

        contentTextProperty().addListener((observable, oldVal, newVal) -> {
            content.setText(newVal);
        });
        amountProperty().addListener((observable, oldVal, newVal) -> {
            amount.setText(format.format(newVal).toString());
        });
        addressProperty().addListener((observable, oldVal, newVal) -> {
            address.setText(newVal.toBase58());
        });

        grid.setHgap(10);
        grid.setVgap(10);
        int row = 0;
        grid.add(content, 0, row++);

        grid.add(amountLabel, 0, row);
        grid.add(amount, 1, row++);

        grid.add(addressLabel, 0, row);
        grid.add(address, 1, row++);

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

    public final void setAddress(final Address address) {
        this.addressProperty.set(address);
    }

    public void setAddressLabel(final String label) {
        this.addressLabel.setText(label);
    }

    public final void setAmount(final Coin amount) {
        this.amountProperty.set(amount);
    }

    public void setAmountLabel(final String label) {
        this.amountLabel.setText(label);
    }
}
