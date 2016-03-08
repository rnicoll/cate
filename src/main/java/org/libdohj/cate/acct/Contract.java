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
 * Class used to determine generic terms of a trade, such as refund period.
 * Used in trade negotiation, once the trade is agreed it takes precedent.
 *
 * @author Ross Nicoll
 */
public class Contract {
    private static final long TWENTY_THREE_HOURS = 23 * 60 * 60 * 1000L;
    private static final long TWENTY_FIVE_HOURS = 25 * 60 * 60 * 1000L;

    private long minLockTime = TWENTY_THREE_HOURS;
    private long maxLockTime = TWENTY_FIVE_HOURS;
    // TODO: Specify mininum fees in asset-amount pairs

    /**
     * @return the minLockTime
     */
    protected long getMinLockTime() {
        return minLockTime;
    }

    /**
     * @param minLockTime the minLockTime to set
     */
    protected void setMinLockTime(long minLockTime) {
        this.minLockTime = minLockTime;
    }

    /**
     * @return the maxLockTime
     */
    protected long getMaxLockTime() {
        return maxLockTime;
    }

    /**
     * @param maxLockTime the maxLockTime to set
     */
    protected void setMaxLockTime(long maxLockTime) {
        this.maxLockTime = maxLockTime;
    }
}
