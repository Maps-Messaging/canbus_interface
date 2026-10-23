/*
 *   Copyright [ 2024 -  2026 ] MapsMessaging B.V.
 *
 *   Licensed under the Apache License, Version 2.0 with the Commons Clause
 *   (the "License"); you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *       https://commonsclause.com/
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package io.mapsmessaging.canbus.device;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuedCanBandwidthLimiterTest {

  @Test
  void oversized_group_accumulates_tokens_across_refills_and_consumes_its_full_cost() throws Exception {
    QueuedCanBandwidthLimiter limiter = new QueuedCanBandwidthLimiter(250_000, 0.1);

    // 250 bits/s: a 1,000-bit group needs four seconds, exceeding the normal 512-bit capacity.
    elapseSeconds(limiter, 2);
    long remainingDelay = limiter.reserveOrDelayNanos(1_000);
    assertTrue(remainingDelay > 0);
    assertTrue(remainingDelay <= TimeUnit.SECONDS.toNanos(2));

    elapseSeconds(limiter, 2);
    assertEquals(0, limiter.reserveOrDelayNanos(1_000));
    assertTrue(limiter.reserveOrDelayNanos(250) > 0, "The whole group must be charged before another message can write");
  }

  @Test
  void normal_group_retains_the_minimum_bucket_capacity_after_an_oversized_group() throws Exception {
    QueuedCanBandwidthLimiter limiter = new QueuedCanBandwidthLimiter(250_000, 0.1);

    elapseSeconds(limiter, 10);
    assertEquals(0, limiter.reserveOrDelayNanos(1_000));

    elapseSeconds(limiter, 10);
    assertEquals(0, limiter.reserveOrDelayNanos(512));
    assertTrue(limiter.reserveOrDelayNanos(250) > 0, "A large group must not permanently enlarge the normal bucket");
  }

  @Test
  void normal_group_retains_the_configured_bucket_capacity() throws Exception {
    QueuedCanBandwidthLimiter limiter = new QueuedCanBandwidthLimiter(250_000, 20.0);

    elapseSeconds(limiter, 10);
    assertEquals(0, limiter.reserveOrDelayNanos(50_000));
    assertTrue(limiter.reserveOrDelayNanos(25_000) > 0);
  }

  private static void elapseSeconds(QueuedCanBandwidthLimiter limiter, long seconds) throws Exception {
    // Advance the existing refill timestamp without sleeping or adding a production clock API.
    Field lastRefillNanos = QueuedCanBandwidthLimiter.class.getDeclaredField("lastRefillNanos");
    lastRefillNanos.setAccessible(true);
    lastRefillNanos.setLong(limiter, System.nanoTime() - TimeUnit.SECONDS.toNanos(seconds));
  }
}
