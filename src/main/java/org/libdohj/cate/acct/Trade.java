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
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;


/**
 * Base class used to describe an atomic cross-chain trade
 * @author Ross Nicoll
 */
public class Trade {
    private final Party leadParty;
    private final Party otherParty;
    private final Input leadInput;
    private final Input otherInput;
    private Contract contract;
    private Optional<Long> lockTime = Optional.empty();
    private final Sha256Hash secretHash;
    private final ECKey leadPublicKey;
    private Optional<ECKey> otherPublicKey;
    private Optional<TransactionOutput> leadTxOutput;
    private Optional<TransactionOutput> otherTxOutput;

    public Trade(final Party leadParty, final Input leadInput,
        final Party otherParty, final Input otherInput,
        final Contract contract,
        final Sha256Hash secretHash, final ECKey leadPublicKey, final Optional<ECKey> otherPublicKey,
        final Optional<TransactionOutput> leadTxOutput, final Optional<TransactionOutput> otherTxOutput) {
        assert leadParty != null;
        assert leadInput != null;
        assert otherParty != null;
        assert otherInput != null;
        assert contract != null;
        assert secretHash != null;
        assert leadPublicKey != null;
        assert otherPublicKey != null;
        assert leadTxOutput != null;
        assert otherTxOutput != null;

        this.leadParty = leadParty;
        this.leadInput = leadInput;
        this.otherParty = otherParty;
        this.otherInput = otherInput;
        this.contract = contract;
        this.secretHash = secretHash;
        this.leadPublicKey = leadPublicKey;
        this.otherPublicKey = otherPublicKey;
        this.leadTxOutput = leadTxOutput;
        this.otherTxOutput = otherTxOutput;
    }

    /**
     * Get the generic contract terms used to negotiate this trade.
     *
     * @return the contract
     */
    public Contract getContract() {
        return contract;
    }

    /**
     * Get the lead party (i.e. the party who determines the secret value used
     * to lock the funding transaction).
     *
     * @return the leadParty
     */
    public Party getLeadParty() {
        return leadParty;
    }

    /**
     * Get details of what the lead party is providing as their part of the trade.
     *
     * @return the asset(s) being provided by the lead party to the other party.
     */
    public Input getLeadInput() {
        return leadInput;
    }

    /**
     * Get the transaction lock time to used when building transactions.
     * @return the lock time to used when building transactions.
     */
    public Optional<Long> getLockTime() {
        return lockTime;
    }

    /**
     * Get the party which is not leading this trade (i.e. the one who does not
     * know the secret value used to lock the funding transaction outputs).
     *
     * @return the otherParty
     */
    public Party getOtherParty() {
        return otherParty;
    }

    /**
     * Get details of what the other party is providing as their part of the trade.
     *
     * @return the asset(s) being provided by the other party to the lead party.
     */
    public Input getOtherInput() {
        return otherInput;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    /**
     * Set the transaction lock time to used when building transactions.
     *
     * @param lockTime the lock time to used when building transactions.
     */
    public void setLockTime(Optional<Long> lockTime) {
        assert lockTime != null;
        this.lockTime = lockTime;
    }

    /**
     * @return the public key of the lead party. Used to secure the funding
     * from the lead party so only they can refund it, and correspondingly to
     * secure the funding transaction from the other party, so only the lead
     * party can claim it.
     */
    public ECKey getLeadPublicKey() {
        return leadPublicKey;
    }

    /**
     * @return the public key of the other party. Used to secure the funding
     * from the other party so only they can refund it, and correspondingly to
     * secure the funding transaction from the lead party, so only the other
     * party can claim it.
     */
    public Optional<ECKey> getOtherPublicKey() {
        return otherPublicKey;
    }

    public void setOtherPublicKey(Optional<ECKey> otherPublicKey) {
        this.otherPublicKey = otherPublicKey;
    }

    public Optional<ECKey> getPublicKey(Party onBehalfOf) {
        if (onBehalfOf.equals(leadParty)) {
            return Optional.of(getLeadPublicKey());
        } else {
            return getOtherPublicKey();
        }
    }

    public Input getInput(Party onBehalfOf) {
        if (onBehalfOf.equals(leadParty)) {
            return getLeadInput();
        } else {
            return getOtherInput();
        }
    }

    public Party getOppositeParty(Party onBehalfOf) {
        if (onBehalfOf.equals(leadParty)) {
            return getOtherParty();
        } else {
            return getLeadParty();
        }
    }

    /**
     * Get the transaction output for the funding transaction.
     *
     * @param onBehalfOf the party who created the funding transaction.
     * @return a transaction output.
     */
    public Optional<TransactionOutput> getFundTransactionOutput(Party onBehalfOf) {
        if (onBehalfOf.equals(leadParty)) {
            return getLeadTxOutput();
        } else {
            return getOtherTxOutput();
        }
    }

    /**
     * Get the transaction output from the lead party's funding transaction.
     *
     * @return the transaction output from the lead party's funding transaction.
     */
    public Optional<TransactionOutput> getLeadTxOutput() {
        return leadTxOutput;
    }

    public void setLeadTxOutput(Optional<TransactionOutput> leadTxOutput) {
        this.leadTxOutput = leadTxOutput;
    }

    /**
     * Get the transaction output from the other party's funding transaction.
     *
     * @return the transaction output from the other party's funding transaction.
     */
    public Optional<TransactionOutput> getOtherTxOutput() {
        return otherTxOutput;
    }

    public void setOtherTxOutput(Optional<TransactionOutput> otherTxOutput) {
        this.otherTxOutput = otherTxOutput;
    }

    /**
     * @return the secretHash
     */
    public Sha256Hash getSecretHash() {
        return secretHash;
    }

    public class Input {
        private final NetworkParameters params;
        private final Coin amount;

        public Input(final NetworkParameters params, final Coin amount) {
            this.params = params;
            this.amount = amount;
        }

        /**
         * Get the network parameters describing the asset being provided as a
         * trade input.
         */
        protected NetworkParameters getParams() {
            return params;
        }

        /**
         * Get the amount of the asset being provided as a trade input.
         */
        protected Coin getAmount() {
            return amount;
        }
    }
}
