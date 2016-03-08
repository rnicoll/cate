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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;

/**
 *
 * @author Ross Nicoll
 */
public interface TransactionFactory {
    public Transaction buildCompletionTransaction(final Trade trade, final Party onBehalfOf,
        final byte[] secret, final Address address);
    public Transaction buildFundTransaction(final Trade trade, final Party onBehalfOf,
        final Sha256Hash secretHash, final Wallet wallet)
            throws InsufficientMoneyException;
    public Transaction buildRefundTransaction(final Trade trade, final Party onBehalfOf,
            final Address address);

    public Script buildFundOutputScript(final Trade trade, final Party onBehalfOf,
        final Sha256Hash secretHash);
}
