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

import lombok.Getter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public final class QueuedCanDeviceStats {

  private final AtomicLong queuedCount;
  private final AtomicLong writtenCount;
  private final AtomicLong droppedCount;
  private final AtomicLong rejectedCount;
  private final AtomicLong writeFailureCount;
  private final AtomicReference<IOException> lastWriteException;

  public QueuedCanDeviceStats() {
    this.queuedCount = new AtomicLong();
    this.writtenCount = new AtomicLong();
    this.droppedCount = new AtomicLong();
    this.rejectedCount = new AtomicLong();
    this.writeFailureCount = new AtomicLong();
    this.lastWriteException = new AtomicReference<>();
  }

  void incrementQueuedCount() {
    queuedCount.incrementAndGet();
  }

  void incrementWrittenCount() {
    writtenCount.incrementAndGet();
  }

  void incrementDroppedCount() {
    droppedCount.incrementAndGet();
  }

  void incrementRejectedCount() {
    rejectedCount.incrementAndGet();
  }

  void recordWriteFailure(IOException exception) {
    writeFailureCount.incrementAndGet();
    lastWriteException.set(exception);
  }

  public IOException getLastWriteException() {
    return lastWriteException.get();
  }

  public long getQueuedCount() {
    return queuedCount.get();
  }

  public long getWrittenCount() {
    return writtenCount.get();
  }

  public long getDroppedCount() {
    return droppedCount.get();
  }

  public long getRejectedCount() {
    return rejectedCount.get();
  }

  public long getWriteFailureCount() {
    return writeFailureCount.get();
  }
}