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
package org.libdohj.cate.cellfactories;

import javafx.beans.property.ReadOnlyObjectPropertyBase;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;

import javafx.util.Callback;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.listeners.WalletCoinEventListener;

/**
 *
 * @author Ross Nicoll
 */
public class WalletBalanceCellFactory implements Callback<TableColumn.CellDataFeatures<Wallet,Coin>,ObservableValue<Coin>> {

    public WalletBalanceCellFactory() {
    }

    @Override
    public ObservableValue<Coin> call(TableColumn.CellDataFeatures<Wallet, Coin> param) {
        final Wallet wallet = param.getValue();
        return new ObservableCoinValue(wallet);
    }

    public static class ObservableCoinValue extends ReadOnlyObjectPropertyBase<Coin> implements WalletCoinEventListener {
        private final Wallet wallet;
        private Coin balance;

        private ObservableCoinValue(final Wallet wallet) {
            this.wallet = wallet;
            this.balance = wallet.getBalance();
        }

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            balance = newBalance;
            fireValueChangedEvent();
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            balance = newBalance;
            fireValueChangedEvent();
        }

        @Override
        public Coin get() {
            return balance;
        }

        @Override
        public Object getBean() {
            return wallet;
        }

        @Override
        public String getName() {
            return "balance";
        }
        
    }
}
