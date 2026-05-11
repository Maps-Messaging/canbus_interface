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
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class QueuedCanDevice implements CanDevice, Closeable {

  private static final int DEFAULT_QUEUE_DEPTH = 128;
  private static final int DEFAULT_BITRATE_BITS_PER_SECOND = 250_000;
  private static final double DEFAULT_MAX_BUS_USAGE_PERCENT = 20.0;
  private static final long DEFAULT_WRITE_FAILURE_BACKOFF_MILLISECONDS  = 25L;

  private static final int CLASSIC_CAN_MAX_PAYLOAD = 8;
  private static final int STANDARD_IDENTIFIER_BITS = 11;
  private static final int EXTENDED_IDENTIFIER_BITS = 29;
  private static final int CLASSIC_BASE_BITS = 47;
  private static final int FD_BASE_BITS = 67;
  private static final double BIT_STUFFING_FACTOR = 1.2;

  private final CanDevice delegate;
  private final BlockingDeque<CanWriteMessage> queue;
  private final QueueFullPolicy queueFullPolicy;
  @Getter
  private final QueuedCanDeviceStats stats;
  private final AtomicBoolean running;
  private final AtomicBoolean closed;
  private final AtomicInteger pendingWriteCount;
  private final Thread writerThread;
  private final QueuedCanBandwidthLimiter bandwidthLimiter;

  @Getter
  private final int queueDepth;

  @Getter
  private final int bitrateBitsPerSecond;

  @Getter
  private final double maxBusUsagePercent;

  @Getter
  private final long writeFailureBackoffMilliseconds;

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
    this(
        delegate,
        queueDepth,
        bitrateBitsPerSecond,
        maxBusUsagePercent,
        queueFullPolicy,
        DEFAULT_WRITE_FAILURE_BACKOFF_MILLISECONDS
    );

  }
  public QueuedCanDevice(CanDevice delegate,
                         int queueDepth,
                         int bitrateBitsPerSecond,
                         double maxBusUsagePercent,
                         QueueFullPolicy queueFullPolicy,
                         long writeFailureBackoffMilliseconds) {

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
    if (writeFailureBackoffMilliseconds < 0) {
      throw new IllegalArgumentException("writeFailureBackoffMilliseconds must be >= 0");
    }
    this.delegate = delegate;
    this.queueDepth = queueDepth;
    this.bitrateBitsPerSecond = bitrateBitsPerSecond;
    this.maxBusUsagePercent = maxBusUsagePercent;
    this.queueFullPolicy = queueFullPolicy;
    this.writeFailureBackoffMilliseconds = writeFailureBackoffMilliseconds;
    this.queue = new LinkedBlockingDeque<>(queueDepth);
    this.stats = new QueuedCanDeviceStats();
    this.running = new AtomicBoolean(true);
    this.closed = new AtomicBoolean(false);
    this.pendingWriteCount = new AtomicInteger();
    this.bandwidthLimiter = new QueuedCanBandwidthLimiter(bitrateBitsPerSecond, maxBusUsagePercent);
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

    writeFrames(List.of(canFrame));
  }

  @Override
  public void writeFrames(List<CanFrame> canFrames) throws IOException {
    if (canFrames == null) {
      throw new IllegalArgumentException("canFrames must not be null");
    }
    if (canFrames.isEmpty()) {
      throw new IllegalArgumentException("canFrames must not be empty");
    }
    if (closed.get()) {
      throw new IOException("QueuedCanDevice is closed");
    }

    int estimatedBits = estimateMessageBits(canFrames);
    CanWriteMessage canWriteMessage = new CanWriteMessage(canFrames, estimatedBits);

    boolean queued = queue.offerLast(canWriteMessage);
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
      CanWriteMessage droppedMessage = queue.pollFirst();
      if (droppedMessage != null) {
        pendingWriteCount.decrementAndGet();
        stats.incrementDroppedCount();
      }

      queued = queue.offerLast(canWriteMessage);
    }

    pendingWriteCount.incrementAndGet();
    stats.incrementQueuedCount();
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
      CanWriteMessage canWriteMessage = null;

      try {
        canWriteMessage = queue.poll(100, TimeUnit.MILLISECONDS);
        if (canWriteMessage == null) {
          continue;
        }

        waitForBandwidth(canWriteMessage);
        writeMessage(canWriteMessage);
        if (queue.isEmpty()) {
          delegate.flush();
        }
        stats.incrementWrittenCount();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (IOException e) {
        stats.recordWriteFailure(e);
        sleepQuietly(writeFailureBackoffMilliseconds);
      } catch (RuntimeException e) {
        IOException exception = new IOException("Unexpected CAN writer failure", e);
        stats.recordWriteFailure(exception);
        sleepQuietly(writeFailureBackoffMilliseconds);
      } finally {
        if (canWriteMessage != null) {
          pendingWriteCount.decrementAndGet();
        }
      }
    }
  }

  private void writeMessage(CanWriteMessage canWriteMessage) throws IOException {
    for (CanFrame canFrame : canWriteMessage.getCanFrames()) {
      delegate.writeFrame(canFrame);
    }
  }

  private void waitForBandwidth(CanWriteMessage canWriteMessage) throws InterruptedException {
    int estimatedBits = canWriteMessage.getEstimatedBits();

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

  private static int estimateMessageBits(List<CanFrame> canFrames) {
    int estimatedBits = 0;

    for (CanFrame canFrame : canFrames) {
      if (canFrame == null) {
        throw new IllegalArgumentException("canFrames must not contain null entries");
      }

      estimatedBits += estimateFrameBits(canFrame);
    }

    return estimatedBits;
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
}