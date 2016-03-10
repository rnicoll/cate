/*
 * Copyright 2016  Ross Nicoll.
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
package org.libdohj.cate.acct;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;

/**
 * Abstract transaction factory which creates transactions for Bitcoin-like
 * blockchains, delegating signature/sigKey construction to implementations.
 *
 * @author Ross Nicoll
 */
public abstract class AbstractTransactionFactory implements TransactionFactory {

    @Override
    public Transaction buildCompletionTransaction(Trade trade, Party onBehalfOf, byte[] secret, Address address)
        throws TradeException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Transaction buildFundTransaction(Trade trade, Party onBehalfOf, Sha256Hash secretHash, Wallet wallet)
        throws InsufficientMoneyException, TradeException {
        final Trade.Input in = trade.getInput(onBehalfOf);
        final Transaction tx = new Transaction(in.getParams());
        tx.addOutput(in.getAmount(), buildFundScriptSigKey(trade, onBehalfOf, secretHash));
        Wallet.SendRequest req = Wallet.SendRequest.forTx(tx);
        wallet.completeTx(req);

        return tx;
    }

    @Override
    public Transaction buildUnsignedRefundTransaction(Trade trade, Party onBehalfOf, Address address)
        throws TradeException {
        final TransactionOutput fundOut = trade.getFundTransactionOutput(onBehalfOf)
                .orElseThrow(() -> new NoFundTransactionException());
        final Transaction transaction = new Transaction(address.getParameters());

        transaction.addInput(fundOut);
        transaction.addOutput(trade.getInput(onBehalfOf).getAmount(), address);

        return transaction;
    }

    @Override
    public TransactionSignature signTransaction(Trade trade, Party onBehalfOf,
            Sha256Hash secretHash, Transaction transaction, int inputIndex, ECKey privateKey)
        throws TradeException{
        assert !privateKey.isPubKeyOnly();

        final Script fundScript = buildFundScriptSigKey(trade, onBehalfOf, secretHash);
        return transaction.calculateSignature(inputIndex,
                privateKey, fundScript, Transaction.SigHash.ALL, false);
    }
}
