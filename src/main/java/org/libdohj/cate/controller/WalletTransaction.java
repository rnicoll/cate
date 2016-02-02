/*
 * Copyright 2016 jrn.
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

import java.text.DateFormat;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.MonetaryFormat;
import org.libdohj.cate.Network;
import org.libdohj.cate.util.NetworkResolver;

/**
 * Class for holding transactions with the additional parts needed to display
 * them in JavaFX.
 *
 * @author Ross Nicoll
 */
public class WalletTransaction extends Object {
    private final Network network;
    private final Transaction transaction;
    private final Coin balanceChange;
    private final ReadOnlyStringProperty networkNameProperty;
    private final ReadOnlyStringProperty dateProperty;
    private final ReadOnlyStringProperty amountProperty;
    private final StringProperty memoProperty;
    private final DateFormat dateFormat;

    protected WalletTransaction(final Network network, final Transaction transaction, final Coin balanceChange) {
        this.network = network;
        this.transaction = transaction;
        this.balanceChange = balanceChange;
        final MonetaryFormat monetaryFormatter = network.getParams().getMonetaryFormat();
        dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        networkNameProperty = new SimpleStringProperty(NetworkResolver.getName(network.getParams()));
        dateProperty = new SimpleStringProperty(dateFormat.format(transaction.getUpdateTime()));
        amountProperty = new SimpleStringProperty(monetaryFormatter.format(balanceChange).toString());
        memoProperty = new SimpleStringProperty(transaction.getMemo());
        memoProperty.addListener(change -> {
            transaction.setMemo(memoProperty.getValue());
        });
    }

    /**
     * @return the network
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * @return the network params
     */
    public NetworkParameters getParams() {
        return network.getParams();
    }

    /**
     * @return the transaction
     */
    public Transaction getTransaction() {
        return transaction;
    }

    /**
     * @return the balance change this transaction causes to the wallet.
     */
    public Coin getBalanceChange() {
        return balanceChange;
    }

    /**
     * @return the property holding the transaction amount.
     */
    public ReadOnlyStringProperty amountProperty() {
        return amountProperty;
    }

    /**
     * @return the property holding the transaction date.
     */
    public ReadOnlyStringProperty dateProperty() {
        return dateProperty;
    }

    /**
     * @return the property holding the name of the network the transaction
     * belongs to.
     */
    public ReadOnlyStringProperty networkNameProperty() {
        return networkNameProperty;
    }

    /**
     * @return the property holding the transaction memo text.
     */
    public StringProperty memoProperty() {
        return memoProperty;
    }

    /**
     * Get the memo from the transaction.
     */
    public String getMemo() {
        return memoProperty.get();
    }

    /**
     * Update the memo on the transaction via its property.
     *
     * @param text new memo text
     */
    public void setMemo(String text) {
        memoProperty.set(text);
    }
    
}
