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

import org.bitcoinj.core.Context;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.DogecoinTestNet3Params;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Unit test for NetworkThreadFactory
 * @author Ross Nicoll
 */
public class NetworkThreadFactoryTest {
    /**
     * Generate a thread and confirm the context is set on it.
     */
    @Test
    public void shouldSetContextOnThread() throws InterruptedException {
        final Context expected = new Context(TestNet3Params.get());
        final Context broken = new Context(DogecoinTestNet3Params.get());
        final NetworkThreadFactory factory = new NetworkThreadFactory(expected);

        // Progagate the wrong context onto this thread
        Context.propagate(broken);
        assertEquals(broken, Context.get());

        final ThreadMonitor monitor = new ThreadMonitor();
        final Thread thread = factory.newThread(monitor);
        thread.run();
        thread.join();
        
        assertEquals(expected, monitor.actual);
    }

    public static class ThreadMonitor implements Runnable {
        Context actual = null;
        public void run() {
            actual = Context.get();
        }
    }
}
