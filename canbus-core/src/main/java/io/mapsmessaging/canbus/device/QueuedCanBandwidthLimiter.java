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

import java.util.concurrent.TimeUnit;

final class QueuedCanBandwidthLimiter {

  private static final int MINIMUM_BUCKET_BITS = 512;

  private final double allowedBitsPerSecond;
  private final double maximumTokenBits;

  private double availableTokenBits;
  private long lastRefillNanos;

  QueuedCanBandwidthLimiter(int bitrateBitsPerSecond, double maxBusUsagePercent) {
    this.allowedBitsPerSecond = bitrateBitsPerSecond * (maxBusUsagePercent / 100.0);
    this.maximumTokenBits = Math.max(MINIMUM_BUCKET_BITS, allowedBitsPerSecond);
    this.availableTokenBits = 0.0;
    this.lastRefillNanos = System.nanoTime();
  }

  synchronized long reserveOrDelayNanos(int requiredBits) {
    refill();

    if (availableTokenBits >= requiredBits) {
      availableTokenBits -= requiredBits;
      return 0L;
    }

    double missingBits = requiredBits - availableTokenBits;
    double waitSeconds = missingBits / allowedBitsPerSecond;
    return (long) Math.ceil(waitSeconds * TimeUnit.SECONDS.toNanos(1));
  }

  private void refill() {
    long currentNanos = System.nanoTime();
    long elapsedNanos = currentNanos - lastRefillNanos;

    if (elapsedNanos <= 0) {
      return;
    }

    double elapsedSeconds = elapsedNanos / (double) TimeUnit.SECONDS.toNanos(1);
    double refillBits = elapsedSeconds * allowedBitsPerSecond;

    availableTokenBits = Math.min(maximumTokenBits, availableTokenBits + refillBits);
    lastRefillNanos = currentNanos;
  }
}