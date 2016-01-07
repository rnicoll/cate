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
package org.libdohj.cate;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import org.bitcoinj.core.NetworkParameters;
import org.junit.Test;

/**
 * Unit tests for NetworkResolver.
 *
 * @author Ross Nicoll
 */
public class NetworkResolverTest {
    /**
     * Tests the lists of networks are not empty.
     */
    @Test
    public void shouldListNetworks() {
        assertFalse(NetworkResolver.getParameters().isEmpty());
        assertFalse(NetworkResolver.getNames().isEmpty());
    }

    /**
     * Tests the resolver maps for internal consistency.
     */
    @Test
    public void shouldMapSymmetrically() {
        for (NetworkParameters params: NetworkResolver.getParameters()) {
            final String name = NetworkResolver.getName(params);
            assertEquals(params, NetworkResolver.getParameter(name));
        }
    }
}
