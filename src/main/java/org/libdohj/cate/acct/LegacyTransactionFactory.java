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

import java.util.Optional;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;

/**
 * Transaction factory for blockchains which do not support the OP_CHECKLOCKTIMEVERIFY
 * op-code.
 *
 * @author Ross Nicoll
 */
public class LegacyTransactionFactory extends AbstractTransactionFactory {
    @Override
    public Script buildFundScriptSigKey(Trade trade, Party onBehalfOf, Sha256Hash secretHash)
        throws TradeException {
        final ScriptBuilder builder = new ScriptBuilder();

        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_HASH160);
        final ECKey publicKey = trade
                .getPublicKey(trade.getOppositeParty(onBehalfOf))
                .orElseThrow(() -> new NoPublicKeyException(onBehalfOf));
        final ECKey oppositePublicKey = trade
                .getPublicKey(trade.getOppositeParty(onBehalfOf))
                .orElseThrow(() -> new NoPublicKeyException(trade.getOppositeParty(onBehalfOf)));
        builder.data(oppositePublicKey.getPubKeyHash());
        builder.op(ScriptOpCodes.OP_EQUALVERIFY);
        builder.op(ScriptOpCodes.OP_CHECKSIGVERIFY);
        builder.op(ScriptOpCodes.OP_IF); // Top of stack is not zero, script matches a pair of signatures
        {
            builder.op(ScriptOpCodes.OP_DUP);
            builder.op(ScriptOpCodes.OP_HASH160);
            builder.data(publicKey.getPubKeyHash());
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

    @Override
    public Script buildCompletionScriptSignature(Trade trade, Party onBehalfOf,
            Transaction transaction, int inputIndex, ECKey privateKey, byte[] secret)
            throws TradeException {
        assert !privateKey.isPubKeyOnly();

        final ScriptBuilder builder = new ScriptBuilder();
        TransactionSignature sig = signTransaction(trade, trade.getOppositeParty(onBehalfOf),
            Sha256Hash.of(secret), transaction, inputIndex, privateKey);
        builder.data(privateKey.getPubKey());
        builder.data(sig.encodeToBitcoin());
        builder.number(0); // Let the script know we want to provide the secret
        builder.data(secret);

        return builder.build();
    }

    @Override
    public Script buildRefundScriptSignature(Trade trade, Party onBehalfOf,
            TransactionSignature mySig, Optional<TransactionSignature> theirSig)
        throws TradeException {
        final ECKey oppositePublicKey = trade
                .getPublicKey(trade.getOppositeParty(onBehalfOf))
                .orElseThrow(() -> new NoPublicKeyException(trade.getOppositeParty(onBehalfOf)));
        final ScriptBuilder builder = new ScriptBuilder();

        builder.data(oppositePublicKey.getPubKey());
        builder.data(mySig.encodeToBitcoin());
        builder.number(1); // Let the script know we want to provide two signatures
        builder.data(trade.getPublicKey(onBehalfOf).get().getPubKey());
        builder.data(theirSig.orElseThrow(() -> new MissingCounterpartySignatureException())
                .encodeToBitcoin());

        return builder.build();
    }

    @Override
    public void completeRefundTransaction(Trade trade, Party onBehalfOf,
            Transaction transaction, TransactionSignature mySig,
            Optional<TransactionSignature> theirSig)
        throws TradeException {
        // TODO: Validate the signatures
        transaction.getInputs().get(0).setScriptSig(this.buildRefundScriptSignature(trade, onBehalfOf, mySig, theirSig));
    }
    
}
