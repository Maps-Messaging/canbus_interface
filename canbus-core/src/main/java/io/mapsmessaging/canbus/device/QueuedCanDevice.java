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

import io.mapsmessaging.canbus.device.frames.CanFrame;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class QueuedCanDevice implements CanDevice, Closeable {

  private static final int DEFAULT_QUEUE_DEPTH = 10;
  private static final int DEFAULT_BITRATE_BITS_PER_SECOND = 250_000;
  private static final double DEFAULT_MAX_BUS_USAGE_PERCENT = 5.0;
  private static final long WRITE_FAILURE_BACKOFF_MILLISECONDS = 100L;
  private static final long FLUSH_POLL_MILLISECONDS = 10L;

  private static final int CLASSIC_CAN_MAX_PAYLOAD = 8;
  private static final int STANDARD_IDENTIFIER_BITS = 11;
  private static final int EXTENDED_IDENTIFIER_BITS = 29;
  private static final int CLASSIC_BASE_BITS = 47;
  private static final int FD_BASE_BITS = 67;
  private static final int MINIMUM_BUCKET_BITS = 512;
  private static final double BIT_STUFFING_FACTOR = 1.2;

  private final CanDevice delegate;
  private final BlockingDeque<CanFrame> queue;
  private final QueueFullPolicy queueFullPolicy;
  @Getter
  private final QueuedCanDeviceStats stats;
  private final AtomicBoolean running;
  private final AtomicBoolean closed;
  private final AtomicInteger pendingWriteCount;
  private final Thread writerThread;
  private final BandwidthLimiter bandwidthLimiter;

  @Getter
  private final int queueDepth;

  @Getter
  private final int bitrateBitsPerSecond;

  @Getter
  private final double maxBusUsagePercent;

  public QueuedCanDevice(CanDevice delegate) {
    this(
        delegate,
        DEFAULT_QUEUE_DEPTH,
        DEFAULT_BITRATE_BITS_PER_SECOND,
        DEFAULT_MAX_BUS_USAGE_PERCENT,
        QueueFullPolicy.DROP_OLDEST
    );
  }

  public QueuedCanDevice(CanDevice delegate,
                         int queueDepth,
                         int bitrateBitsPerSecond,
                         double maxBusUsagePercent,
                         QueueFullPolicy queueFullPolicy) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    if (queueDepth <= 0) {
      throw new IllegalArgumentException("queueDepth must be > 0");
    }
    if (bitrateBitsPerSecond <= 0) {
      throw new IllegalArgumentException("bitrateBitsPerSecond must be > 0");
    }
    if (maxBusUsagePercent <= 0.0 || maxBusUsagePercent > 100.0) {
      throw new IllegalArgumentException("maxBusUsagePercent must be > 0 and <= 100");
    }
    if (queueFullPolicy == null) {
      throw new IllegalArgumentException("queueFullPolicy must not be null");
    }

    this.delegate = delegate;
    this.queueDepth = queueDepth;
    this.bitrateBitsPerSecond = bitrateBitsPerSecond;
    this.maxBusUsagePercent = maxBusUsagePercent;
    this.queueFullPolicy = queueFullPolicy;
    this.queue = new LinkedBlockingDeque<>(queueDepth);
    this.stats = new QueuedCanDeviceStats();
    this.running = new AtomicBoolean(true);
    this.closed = new AtomicBoolean(false);
    this.pendingWriteCount = new AtomicInteger();
    this.bandwidthLimiter = new BandwidthLimiter(bitrateBitsPerSecond, maxBusUsagePercent);
    this.writerThread = new Thread(this::runWriter, "queued-can-device-writer");
    this.writerThread.setDaemon(true);
    this.writerThread.start();
  }

  public static QueuedCanDevice withDefaults(CanDevice delegate) {
    return new QueuedCanDevice(delegate);
  }

  @Override
  public CanFrame readFrame() throws IOException {
    return delegate.readFrame();
  }

  @Override
  public void writeFrame(CanFrame canFrame) throws IOException {
    if (canFrame == null) {
      throw new IllegalArgumentException("canFrame must not be null");
    }
    if (closed.get()) {
      throw new IOException("QueuedCanDevice is closed");
    }

    boolean queued = queue.offerLast(canFrame);
    if (queued) {
      pendingWriteCount.incrementAndGet();
      stats.incrementQueuedCount();
      return;
    }

    if (queueFullPolicy == QueueFullPolicy.REJECT_NEW) {
      stats.incrementRejectedCount();
      throw new IOException("CAN write queue is full");
    }

    while (!queued) {
      CanFrame droppedFrame = queue.pollFirst();
      if (droppedFrame != null) {
        pendingWriteCount.decrementAndGet();
        stats.incrementDroppedCount();
      }
      queued = queue.offerLast(canFrame);
    }

    pendingWriteCount.incrementAndGet();
    stats.incrementQueuedCount();
  }

  @Override
  public void flush() throws IOException {
    while (pendingWriteCount.get() > 0 && running.get()) {
      sleepQuietly(FLUSH_POLL_MILLISECONDS);
    }
    delegate.flush();
  }

  @Override
  public CanCapabilities getCanCapabilities() {
    return delegate.getCanCapabilities();
  }

  public int getCurrentQueueDepth() {
    return queue.size();
  }

  public int getPendingWriteCount() {
    return pendingWriteCount.get();
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    running.set(false);
    writerThread.interrupt();

    try {
      writerThread.join(TimeUnit.SECONDS.toMillis(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    delegate.close();
  }

  private void runWriter() {
    while (running.get()) {
      CanFrame canFrame = null;

      try {
        canFrame = queue.poll(100, TimeUnit.MILLISECONDS);
        if (canFrame == null) {
          continue;
        }

        waitForBandwidth(canFrame);
        delegate.writeFrame(canFrame);
        stats.incrementWrittenCount();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        stats.recordWriteFailure(e);
        sleepQuietly(WRITE_FAILURE_BACKOFF_MILLISECONDS);
      } catch (RuntimeException e) {
        IOException exception = new IOException("Unexpected CAN writer failure", e);
        stats.recordWriteFailure(exception);
        sleepQuietly(WRITE_FAILURE_BACKOFF_MILLISECONDS);
      } finally {
        if (canFrame != null) {
          pendingWriteCount.decrementAndGet();
        }
      }
    }
  }

  private void waitForBandwidth(CanFrame canFrame) throws InterruptedException {
    int estimatedBits = estimateFrameBits(canFrame);

    while (running.get()) {
      long waitNanos = bandwidthLimiter.reserveOrDelayNanos(estimatedBits);
      if (waitNanos <= 0) {
        return;
      }

      long boundedWaitNanos = Math.min(waitNanos, TimeUnit.MILLISECONDS.toNanos(100));
      LockSupport.parkNanos(boundedWaitNanos);

      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
    }
  }

  private static int estimateFrameBits(CanFrame canFrame) {
    int identifierBits = canFrame.extendedFrame() ? EXTENDED_IDENTIFIER_BITS : STANDARD_IDENTIFIER_BITS;
    int payloadBits = canFrame.dataLengthCode() * Byte.SIZE;
    int baseBits = canFrame.dataLengthCode() <= CLASSIC_CAN_MAX_PAYLOAD ? CLASSIC_BASE_BITS : FD_BASE_BITS;
    double estimatedBits = (baseBits + identifierBits + payloadBits) * BIT_STUFFING_FACTOR;
    return (int) Math.ceil(estimatedBits);
  }

  private static void sleepQuietly(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static final class BandwidthLimiter {

    private final double allowedBitsPerSecond;
    private final double maximumTokenBits;

    private double availableTokenBits;
    private long lastRefillNanos;

    private BandwidthLimiter(int bitrateBitsPerSecond, double maxBusUsagePercent) {
      this.allowedBitsPerSecond = bitrateBitsPerSecond * (maxBusUsagePercent / 100.0);
      this.maximumTokenBits = Math.max(MINIMUM_BUCKET_BITS, allowedBitsPerSecond);
      this.availableTokenBits = 0.0;
      this.lastRefillNanos = System.nanoTime();
    }

    private synchronized long reserveOrDelayNanos(int requiredBits) {
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
}