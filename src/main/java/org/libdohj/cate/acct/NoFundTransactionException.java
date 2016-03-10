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

/**
 * Exception when a method requires a funding transaction to exist, but none
 * is available.
 *
 * @author Ross Nicoll
 */
public class NoFundTransactionException extends TradeException {

    public NoFundTransactionException() {
        super("No funding transaction is available, it must be seen before it can be spent.");
    }
    
}
