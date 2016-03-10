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
package org.libdohj.cate.acct;

import java.util.Optional;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;

/**
 *
 * @author Ross Nicoll
 */
public interface TransactionFactory {
    public Transaction buildCompletionTransaction(final Trade trade, final Party onBehalfOf,
        final byte[] secret, final Address address) throws TradeException;
    public Transaction buildFundTransaction(final Trade trade, final Party onBehalfOf,
        final Sha256Hash secretHash, final Wallet wallet)
            throws InsufficientMoneyException, TradeException;
    /**
     * Build, but do not sign, the refund transaction. This is detached from
     * signing as the transaction may need to be relayed to other parties for
     * further signatures to be provided.
     *
     * @param trade the trade to build the refund transaction for.
     * @param onBehalfOf the party this transaction is on behalf of.
     * @param address the address to send the funds to.
     * @return an unsigned transaction.
     * @see #signTransaction(org.libdohj.cate.acct.Trade, org.libdohj.cate.acct.Party, org.bitcoinj.core.Sha256Hash, org.bitcoinj.core.Transaction, int, org.bitcoinj.core.ECKey)
     * @throws TradeException 
     */
    public Transaction buildUnsignedRefundTransaction(final Trade trade, final Party onBehalfOf,
            final Address address)
            throws TradeException;

    public Script buildFundScriptSigKey(final Trade trade, final Party onBehalfOf,
        final Sha256Hash secretHash)
            throws TradeException;

    /**
     * Build the script signature to unlock the fund transaction, as a completion
     * transaction. This is used for the party who sent the fund transaction to
     * reclaim their funds after the trade expires.
     *
     * @param trade the trade to complete.
     * @param onBehalfOf the party this transaction is on behalf of.
     * @param transaction the unsigned completion transaction.
     * @param privateKey the private key used to sign the transaction.
     * @param secret secret bytes used to unlock the input transaction.
     * @return the signature for the refund transaction.
     */
    public Script buildCompletionScriptSignature(final Trade trade, final Party onBehalfOf,
        final Transaction transaction, int inputIndex, final ECKey privateKey, final byte[] secret)
            throws TradeException;

    /**
     * Build the script signature to unlock the fund transaction, as a refund
     * transaction. This is used for the party who sent the fund transaction to
     * reclaim their funds after the trade expires.
     *
     * @param trade the trade to generate a refund for.
     * @param onBehalfOf the party this transaction is on behalf of.
     * @param mySig signature of the funding party.
     * @param theirSig signature of the opposite party.
     * @return the signature for the refund transaction.
     */
    public Script buildRefundScriptSignature(Trade trade, Party onBehalfOf,
            TransactionSignature mySig, Optional<TransactionSignature> theirSig)
            throws TradeException;

    public void completeRefundTransaction(final Trade trade, final Party onBehalfOf,
            final Transaction transaction, 
            TransactionSignature mySig, Optional<TransactionSignature> theirSig)
            throws TradeException;

    public TransactionSignature signTransaction(Trade trade, Party onBehalfOf,
        Sha256Hash secretHash, Transaction transaction, int inputIndex, ECKey privateKey)
            throws TradeException;
}
