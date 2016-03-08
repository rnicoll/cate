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

import javax.annotation.Nullable;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;

/**
 * Transaction factory for blockchains which do not support the OP_CHECKLOCKTIMEVERIFY
 * op-code.
 *
 * @author Ross Nicoll
 */
public class LegacyTransactionFactory implements TransactionFactory {

    @Override
    public Transaction buildCompletionTransaction(Trade trade, Party onBehalfOf, byte[] secret, Address address) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Transaction buildFundTransaction(Trade trade, Party onBehalfOf, Sha256Hash secretHash, Wallet wallet)
        throws InsufficientMoneyException {
        final Trade.Input in = trade.getInput(onBehalfOf);
        final Transaction tx = new Transaction(in.getParams());
        final TransactionOutput out = new TransactionOutput(in.getParams(), tx, in.getAmount(),
            buildFundOutputScript(trade, onBehalfOf, secretHash).getProgram());
        Wallet.SendRequest req = Wallet.SendRequest.forTx(tx);
        wallet.completeTx(req);

        return tx;
    }

    @Override
    public Transaction buildRefundTransaction(Trade trade, Party onBehalfOf, Address address) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Script buildFundOutputScript(Trade trade, Party onBehalfOf, Sha256Hash secretHash) {
        final ScriptBuilder builder = new ScriptBuilder();

        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_HASH160);
        builder.data(trade.getOppositePublicKey(onBehalfOf).get().getPubKeyHash());
        builder.op(ScriptOpCodes.OP_EQUALVERIFY);
        builder.op(ScriptOpCodes.OP_CHECKSIGVERIFY);
        builder.op(ScriptOpCodes.OP_IF); // Top of stack is not zero, script matches a pair of signatures
        {
            builder.op(ScriptOpCodes.OP_DUP);
            builder.op(ScriptOpCodes.OP_HASH160);
            builder.data(trade.getPublicKey(onBehalfOf).get().getPubKeyHash());
            builder.op(ScriptOpCodes.OP_EQUALVERIFY);
            builder.op(ScriptOpCodes.OP_CHECKSIG);
        }
        builder.op(ScriptOpCodes.OP_ELSE);
        {
            // Secret and single signature
            builder.op(ScriptOpCodes.OP_HASH256);
            builder.data(secretHash.getReversedBytes());
            builder.op(ScriptOpCodes.OP_EQUAL);
        }
        builder.op(ScriptOpCodes.OP_ENDIF);

        return builder.build();
    }
    
}
