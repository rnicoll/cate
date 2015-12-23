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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Wallet;
import org.libdohj.cate.controller.MainController;

/**
 *
 * @author Ross Nicoll
 */
public class WalletNetworkCellFactory implements Callback<TableColumn.CellDataFeatures<Wallet,String>,ObservableValue<String>> {

    public WalletNetworkCellFactory() {
    }

    @Override
    public ObservableValue<String> call(TableColumn.CellDataFeatures<Wallet, String> param) {
        final Wallet wallet = param.getValue();
        final NetworkParameters params = wallet.getParams();
        return new SimpleStringProperty(MainController.getNetworkName(params));
    }
    
}
