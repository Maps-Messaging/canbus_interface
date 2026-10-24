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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuedCanDeviceTest {

  @Test
  void shouldCancelDequeuedGroupWhenClosedBeforeBandwidthWaitStarts() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate, 10, 250_000, 100.0, QueueFullPolicy.REJECT_NEW);
    PausingWriteQueue queue = new PausingWriteQueue();
    Field queueField = QueuedCanDevice.class.getDeclaredField("queue");
    queueField.setAccessible(true);
    queueField.set(queuedCanDevice, queue);
    ExecutorService closer = Executors.newSingleThreadExecutor();

    try {
      queuedCanDevice.writeFrames(List.of(createFrame(10), createFrame(11), createFrame(12)));
      assertTrue(queue.dequeued.await(5, TimeUnit.SECONDS));

      Future<Void> closeResult = closer.submit(() -> {
        queuedCanDevice.close();
        return null;
      });
      assertTrue(queue.interrupted.await(5, TimeUnit.SECONDS));
      queue.release.countDown();
      closeResult.get(5, TimeUnit.SECONDS);

      assertTrue(delegate.isClosed());
      assertEquals(List.of(), delegate.getWrittenFrames());
      assertEquals(0, queuedCanDevice.getPendingWriteCount());
      assertEquals(0, queuedCanDevice.getStats().getWrittenCount());
      assertEquals(0, queuedCanDevice.getStats().getWriteFailureCount());
    } finally {
      queue.release.countDown();
      closer.shutdownNow();
      try {
        assertTrue(closer.awaitTermination(5, TimeUnit.SECONDS));
      } finally {
        queuedCanDevice.close();
      }
    }
  }

  @Test
  void shouldKeepConcurrentGroupsAndSingleFramesContiguousThroughTheQueue() throws Exception {
    BlockingTestCanDevice delegate = new BlockingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate, 128, 250_000, 20.0, QueueFullPolicy.REJECT_NEW);
    ExecutorService producers = Executors.newFixedThreadPool(4);
    CountDownLatch ready = new CountDownLatch(4);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Void>> submissions = new ArrayList<>();
    List<Integer> expectedIdentifiers = new ArrayList<>();

    try {
      queuedCanDevice.writeFrame(createFrame(0));
      assertEventually(() -> delegate.getWriteAttempts() == 1);

      for (int producer = 0; producer < 4; producer++) {
        int producerIndex = producer;
        for (int message = 0; message < 8; message++) {
          int firstIdentifier = (producer * 8 + message + 1) * 10;
          expectedIdentifiers.addAll(List.of(firstIdentifier, firstIdentifier + 1, firstIdentifier + 2, firstIdentifier + 9));
        }
        submissions.add(producers.submit(() -> {
          ready.countDown();
          assertTrue(start.await(5, TimeUnit.SECONDS));
          for (int message = 0; message < 8; message++) {
            int firstIdentifier = (producerIndex * 8 + message + 1) * 10;
            queuedCanDevice.writeFrames(List.of(createFrame(firstIdentifier), createFrame(firstIdentifier + 1), createFrame(firstIdentifier + 2)));
            queuedCanDevice.writeFrame(createFrame(firstIdentifier + 9));
          }
          return null;
        }));
      }

      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (Future<Void> submission : submissions) {
        submission.get(5, TimeUnit.SECONDS);
      }
      delegate.releaseWrites();
      assertEventually(() -> queuedCanDevice.getPendingWriteCount() == 0, 5_000);

      List<Integer> identifiers = delegate.getWrittenFrames().stream().map(CanFrame::canIdentifier).toList();
      assertEquals(0, identifiers.getFirst());
      assertEquals(expectedIdentifiers.stream().sorted().toList(), identifiers.subList(1, identifiers.size()).stream().sorted().toList());
      for (int position = 1; position < identifiers.size(); position++) {
        int identifier = identifiers.get(position);
        if (identifier % 10 == 0) {
          assertEquals(List.of(identifier, identifier + 1, identifier + 2), identifiers.subList(position, position + 3));
          position += 2;
        } else {
          assertEquals(9, identifier % 10, "Only a complete group or a single frame may start here");
        }
      }
      assertEquals(65, queuedCanDevice.getStats().getWrittenCount());
      assertEquals(0, queuedCanDevice.getStats().getWriteFailureCount());
    } finally {
      start.countDown();
      delegate.releaseWrites();
      producers.shutdownNow();
      try {
        assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));
      } finally {
        queuedCanDevice.close();
      }
    }
  }

  @Test
  void shouldRecordPartialGroupFailureWithoutRetryingItsPrefixOrWritingItsTail() throws Exception {
    FailingMidGroupTestCanDevice delegate = new FailingMidGroupTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate, 10, 250_000, 100.0, QueueFullPolicy.REJECT_NEW, 0);

    try {
      queuedCanDevice.writeFrames(List.of(createFrame(10), createFrame(11), createFrame(12)));
      queuedCanDevice.writeFrames(List.of(createFrame(20), createFrame(21)));
      assertEventually(() -> queuedCanDevice.getPendingWriteCount() == 0);

      assertEquals(List.of(10, 20, 21), delegate.getWrittenFrames().stream().map(CanFrame::canIdentifier).toList());
      assertEquals(1, queuedCanDevice.getStats().getWriteFailureCount());
      assertSame(delegate.failure, queuedCanDevice.getStats().getLastWriteException());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldCancelAGroupWaitingForBandwidthWhenClosed() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate, 10, 250_000, 0.000001, QueueFullPolicy.REJECT_NEW);

    try {
      queuedCanDevice.writeFrames(List.of(createFrame(10), createFrame(11), createFrame(12), createFrame(13)));
      Field writerThreadField = QueuedCanDevice.class.getDeclaredField("writerThread");
      writerThreadField.setAccessible(true);
      Thread writerThread = (Thread) writerThreadField.get(queuedCanDevice);
      assertEventually(() -> isWaitingForBandwidth(writerThread));
      assertEquals(1, queuedCanDevice.getPendingWriteCount());

      queuedCanDevice.close();

      assertTrue(delegate.isClosed());
      assertEquals(List.of(), delegate.getWrittenFrames());
      assertEquals(0, queuedCanDevice.getPendingWriteCount());
      assertEquals(0, queuedCanDevice.getStats().getWrittenCount());
      assertEquals(0, queuedCanDevice.getStats().getWriteFailureCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldWriteGroupLargerThanBandwidthBucketAndContinueWithNextMessage() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate, 10, 250_000, 0.1, QueueFullPolicy.REJECT_NEW);
    List<CanFrame> group = List.of(createFrame(10), createFrame(11), createFrame(12), createFrame(13));
    CanFrame followingFrame = createFrame(20);

    try {
      // Four extended eight-byte frames exceed the minimum 512-bit bucket.
      queuedCanDevice.writeFrames(group);
      queuedCanDevice.writeFrame(followingFrame);

      assertEventually(() -> queuedCanDevice.getPendingWriteCount() == 0, 6_000);

      List<CanFrame> expected = new ArrayList<>(group);
      expected.add(followingFrame);
      assertEquals(expected, delegate.getWrittenFrames());
      assertEquals(2, queuedCanDevice.getStats().getWrittenCount());
      assertEquals(0, queuedCanDevice.getStats().getWriteFailureCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldWriteQueuedFrameToDelegate() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      CanFrame canFrame = createFrame(1);

      queuedCanDevice.writeFrame(canFrame);

      assertEventually(() -> delegate.getWrittenFrames().size() == 1);
      assertEquals(canFrame, delegate.getWrittenFrames().get(0));
      assertEquals(1, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldWriteQueuedFramesAsOneLogicalMessage() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      List<CanFrame> canFrames = List.of(
          createFrame(1),
          createFrame(2),
          createFrame(3)
      );

      queuedCanDevice.writeFrames(canFrames);

      assertEventually(() -> delegate.getWrittenFrames().size() == 3);

      assertEquals(canFrames, delegate.getWrittenFrames());
      assertEquals(1, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
      assertEquals(0, queuedCanDevice.getPendingWriteCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldWriteGroupedFramesBackToBackWithoutPerFrameLimiterDelay() throws Exception {
    TimingTestCanDevice delegate = new TimingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        0.1,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      List<CanFrame> canFrames = List.of(
          createFrame(1),
          createFrame(2)
      );

      queuedCanDevice.writeFrames(canFrames);

      assertEventually(() -> delegate.getWrittenFrames().size() == 2, 3_000);

      long firstWriteNanos = delegate.getWriteTimesNanos().get(0);
      long secondWriteNanos = delegate.getWriteTimesNanos().get(1);
      long gapMilliseconds = TimeUnit.NANOSECONDS.toMillis(secondWriteNanos - firstWriteNanos);

      assertTrue(gapMilliseconds < 100);
      assertEquals(1, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRejectNullFrameList() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate);

    try {
      assertThrows(IllegalArgumentException.class, () -> queuedCanDevice.writeFrames(null));
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRejectEmptyFrameList() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate);

    try {
      assertThrows(IllegalArgumentException.class, () -> queuedCanDevice.writeFrames(List.of()));
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRejectFrameListContainingNull() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate);

    try {
      List<CanFrame> canFrames = new ArrayList<>();
      canFrames.add(createFrame(1));
      canFrames.add(null);

      assertThrows(IllegalArgumentException.class, () -> queuedCanDevice.writeFrames(canFrames));
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldDropOldestFrameWhenQueueIsFull() throws Exception {
    BlockingTestCanDevice delegate = new BlockingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        2,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      CanFrame firstFrame = createFrame(1);
      CanFrame secondFrame = createFrame(2);
      CanFrame thirdFrame = createFrame(3);
      CanFrame fourthFrame = createFrame(4);

      queuedCanDevice.writeFrame(firstFrame);
      assertEventually(() -> delegate.getWriteAttempts() == 1);

      queuedCanDevice.writeFrame(secondFrame);
      queuedCanDevice.writeFrame(thirdFrame);
      queuedCanDevice.writeFrame(fourthFrame);

      delegate.releaseWrites();

      assertEventually(() -> delegate.getWrittenFrames().size() == 3);

      List<CanFrame> writtenFrames = delegate.getWrittenFrames();

      assertEquals(firstFrame, writtenFrames.get(0));
      assertEquals(thirdFrame, writtenFrames.get(1));
      assertEquals(fourthFrame, writtenFrames.get(2));
      assertEquals(1, queuedCanDevice.getStats().getDroppedCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldDropOldestLogicalMessageWhenQueueIsFull() throws Exception {
    BlockingTestCanDevice delegate = new BlockingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        2,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      List<CanFrame> firstMessage = List.of(
          createFrame(10),
          createFrame(11)
      );
      List<CanFrame> secondMessage = List.of(
          createFrame(20),
          createFrame(21)
      );
      List<CanFrame> thirdMessage = List.of(
          createFrame(30),
          createFrame(31)
      );
      List<CanFrame> fourthMessage = List.of(
          createFrame(40),
          createFrame(41)
      );

      queuedCanDevice.writeFrames(firstMessage);
      assertEventually(() -> delegate.getWriteAttempts() == 1);

      queuedCanDevice.writeFrames(secondMessage);
      queuedCanDevice.writeFrames(thirdMessage);
      queuedCanDevice.writeFrames(fourthMessage);

      delegate.releaseWrites();

      assertEventually(() -> delegate.getWrittenFrames().size() == 6);

      List<CanFrame> expectedFrames = new ArrayList<>();
      expectedFrames.addAll(firstMessage);
      expectedFrames.addAll(thirdMessage);
      expectedFrames.addAll(fourthMessage);

      assertEquals(expectedFrames, delegate.getWrittenFrames());
      assertEquals(1, queuedCanDevice.getStats().getDroppedCount());
      assertEquals(4, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(3, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRejectNewestFrameWhenQueueIsFullAndPolicyIsRejectNew() throws Exception {
    BlockingTestCanDevice delegate = new BlockingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        1,
        250_000,
        100.0,
        QueueFullPolicy.REJECT_NEW
    );

    try {
      queuedCanDevice.writeFrame(createFrame(1));
      assertEventually(() -> delegate.getWriteAttempts() == 1);

      queuedCanDevice.writeFrame(createFrame(2));

      IOException exception = assertThrows(
          IOException.class,
          () -> queuedCanDevice.writeFrame(createFrame(3))
      );

      assertTrue(exception.getMessage().contains("full"));
      assertEquals(1, queuedCanDevice.getStats().getRejectedCount());

      delegate.releaseWrites();
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRejectNewestLogicalMessageWhenQueueIsFullAndPolicyIsRejectNew() throws Exception {
    BlockingTestCanDevice delegate = new BlockingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        1,
        250_000,
        100.0,
        QueueFullPolicy.REJECT_NEW
    );

    try {
      queuedCanDevice.writeFrames(List.of(createFrame(1), createFrame(2)));
      assertEventually(() -> delegate.getWriteAttempts() == 1);

      queuedCanDevice.writeFrames(List.of(createFrame(3), createFrame(4)));

      IOException exception = assertThrows(
          IOException.class,
          () -> queuedCanDevice.writeFrames(List.of(createFrame(5), createFrame(6)))
      );

      assertTrue(exception.getMessage().contains("full"));
      assertEquals(1, queuedCanDevice.getStats().getRejectedCount());
      assertEquals(2, queuedCanDevice.getStats().getQueuedCount());

      delegate.releaseWrites();

      assertEventually(() -> delegate.getWrittenFrames().size() == 4);
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldDelegateReadFrame() throws Exception {
    CanFrame expectedFrame = createFrame(55);
    TestCanDevice delegate = new TestCanDevice();
    delegate.setReadFrame(expectedFrame);

    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate);

    try {
      CanFrame actualFrame = queuedCanDevice.readFrame();

      assertEquals(expectedFrame, actualFrame);
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldFlushDelegateWhenWriterQueueBecomesEmptyAfterSingleMessage() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      queuedCanDevice.writeFrame(createFrame(1));

      assertEventually(() -> delegate.getWrittenFrames().size() == 1);
      assertEventually(() -> delegate.getFlushCount() == 1);

      assertEquals(1, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldNotDrainQueueWhenFlushIsCalled() throws Exception {
    BlockingTestCanDevice delegate = new BlockingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      List<CanFrame> firstMessage = List.of(
          createFrame(1),
          createFrame(2)
      );

      queuedCanDevice.writeFrames(firstMessage);

      assertEventually(() -> delegate.getWriteAttempts() == 1);

      queuedCanDevice.flush();

      assertEquals(0, delegate.getWrittenFrames().size());

      delegate.releaseWrites();

      assertEventually(() -> delegate.getWrittenFrames().size() == 2);

      assertEquals(firstMessage, delegate.getWrittenFrames());
      assertEquals(1, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldNotUseFlushToBypassBandwidthThrottle() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        0.1,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      long startTime = System.nanoTime();

      queuedCanDevice.writeFrame(createFrame(1));
      queuedCanDevice.writeFrame(createFrame(2));
      queuedCanDevice.flush();

      long flushElapsedMilliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

      assertTrue(flushElapsedMilliseconds < 400);

      assertEventually(() -> delegate.getWrittenFrames().size() == 2, 3_000);

      long totalElapsedMilliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

      assertTrue(totalElapsedMilliseconds >= 400);
      assertEquals(2, delegate.getWrittenFrames().size());
      assertEquals(1, delegate.getFlushCount());
    } finally {
      queuedCanDevice.close();
    }
  }
  @Test
  void shouldCloseDelegate() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate);

    queuedCanDevice.close();

    assertTrue(delegate.isClosed());
  }

  @Test
  void shouldRejectWritesAfterClose() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(delegate);

    queuedCanDevice.close();

    assertThrows(IOException.class, () -> queuedCanDevice.writeFrame(createFrame(1)));
    assertThrows(IOException.class, () -> queuedCanDevice.writeFrames(List.of(createFrame(2))));
  }

  @Test
  void shouldLimitWriteRateBetweenLogicalMessages() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        0.1,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      long startTime = System.nanoTime();

      queuedCanDevice.writeFrame(createFrame(1));
      queuedCanDevice.writeFrame(createFrame(2));

      assertEventually(() -> delegate.getWrittenFrames().size() == 2, 3_000);

      long elapsedMilliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

      assertTrue(elapsedMilliseconds >= 400);
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldLimitWriteRateForWholeLogicalMessageAndWriteFramesBackToBack() throws Exception {
    TimingTestCanDevice delegate = new TimingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        0.1,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      long startTime = System.nanoTime();

      queuedCanDevice.writeFrames(List.of(createFrame(1), createFrame(2)));

      assertEventually(() -> delegate.getWrittenFrames().size() == 2, 5_000);

      long elapsedMilliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
      long firstWriteNanos = delegate.getWriteTimesNanos().get(0);
      long secondWriteNanos = delegate.getWriteTimesNanos().get(1);
      long gapMilliseconds = TimeUnit.NANOSECONDS.toMillis(secondWriteNanos - firstWriteNanos);

      assertTrue(elapsedMilliseconds >= 1_000);
      assertTrue(gapMilliseconds < 100);
      assertEquals(1, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRecordWriteFailureAndContinue() throws Exception {
    FailingOnceTestCanDevice delegate = new FailingOnceTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      queuedCanDevice.writeFrame(createFrame(1));
      queuedCanDevice.writeFrame(createFrame(2));

      assertEventually(() -> delegate.getWrittenFrames().size() == 1);

      assertEquals(1, queuedCanDevice.getStats().getWriteFailureCount());
      assertTrue(queuedCanDevice.getStats().getLastWriteException() != null);
      assertEquals(2, delegate.getWrittenFrames().get(0).canIdentifier());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRecordGroupedWriteFailureAndContinueWithNextLogicalMessage() throws Exception {
    FailingOnceTestCanDevice delegate = new FailingOnceTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST
    );

    try {
      queuedCanDevice.writeFrames(List.of(createFrame(1), createFrame(2)));
      queuedCanDevice.writeFrames(List.of(createFrame(3), createFrame(4)));

      assertEventually(() -> delegate.getWrittenFrames().size() == 2);

      assertEquals(1, queuedCanDevice.getStats().getWriteFailureCount());
      assertTrue(queuedCanDevice.getStats().getLastWriteException() != null);
      assertEquals(3, delegate.getWrittenFrames().get(0).canIdentifier());
      assertEquals(4, delegate.getWrittenFrames().get(1).canIdentifier());
      assertEquals(2, queuedCanDevice.getStats().getQueuedCount());
      assertEquals(1, queuedCanDevice.getStats().getWrittenCount());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldUseConfiguredWriteFailureBackoff() throws Exception {
    AlwaysFailingTestCanDevice delegate = new AlwaysFailingTestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST,
        250L
    );

    try {
      queuedCanDevice.writeFrame(createFrame(1));
      queuedCanDevice.writeFrame(createFrame(2));

      assertEventually(() -> delegate.getWriteAttempts() >= 1);

      long firstAttemptTimeNanos = delegate.getWriteAttemptTimesNanos().get(0);

      assertEventually(() -> delegate.getWriteAttempts() >= 2, 1_500);

      long secondAttemptTimeNanos = delegate.getWriteAttemptTimesNanos().get(1);
      long gapMilliseconds = TimeUnit.NANOSECONDS.toMillis(secondAttemptTimeNanos - firstAttemptTimeNanos);

      assertTrue(gapMilliseconds >= 200);
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldExposeConfiguredWriteFailureBackoff() throws Exception {
    TestCanDevice delegate = new TestCanDevice();
    QueuedCanDevice queuedCanDevice = new QueuedCanDevice(
        delegate,
        10,
        250_000,
        100.0,
        QueueFullPolicy.DROP_OLDEST,
        123L
    );

    try {
      assertEquals(123L, queuedCanDevice.getWriteFailureBackoffMilliseconds());
    } finally {
      queuedCanDevice.close();
    }
  }

  @Test
  void shouldRejectNegativeWriteFailureBackoff() {
    TestCanDevice delegate = new TestCanDevice();

    assertThrows(
        IllegalArgumentException.class,
        () -> new QueuedCanDevice(
            delegate,
            10,
            250_000,
            100.0,
            QueueFullPolicy.DROP_OLDEST,
            -1L
        )
    );
  }

  private static CanFrame createFrame(int canIdentifier) {
    byte[] data = new byte[]{
        (byte) canIdentifier,
        2,
        3,
        4,
        5,
        6,
        7,
        8
    };

    return new CanFrame(canIdentifier, true, data.length, data);
  }

  private static boolean isWaitingForBandwidth(Thread writerThread) {
    if (writerThread.getState() != Thread.State.TIMED_WAITING) {
      return false;
    }
    for (StackTraceElement frame : writerThread.getStackTrace()) {
      if (frame.getClassName().equals(QueuedCanDevice.class.getName()) && frame.getMethodName().equals("waitForBandwidth")) {
        return true;
      }
    }
    return false;
  }

  private static void assertEventually(BooleanSupplier condition) throws InterruptedException {
    assertEventually(condition, 2_000);
  }

  private static void assertEventually(BooleanSupplier condition, long timeoutMilliseconds) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMilliseconds);

    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }

      Thread.sleep(10);
    }

    assertTrue(condition.getAsBoolean());
  }

  private static final class PausingWriteQueue extends LinkedBlockingDeque<CanWriteMessage> {

    private final CountDownLatch dequeued = new CountDownLatch(1);
    private final CountDownLatch interrupted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    public CanWriteMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
      CanWriteMessage message = super.poll(timeout, unit);
      if (message == null) {
        return null;
      }

      // Reproduce shutdown after a successful dequeue, before waitForBandwidth is entered.
      dequeued.countDown();
      boolean restoreInterrupt = false;
      boolean released = false;
      while (!released) {
        try {
          if (!release.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out releasing the dequeued group");
          }
          released = true;
        } catch (InterruptedException exception) {
          restoreInterrupt = true;
          interrupted.countDown();
        }
      }
      if (restoreInterrupt) {
        Thread.currentThread().interrupt();
      }
      return message;
    }
  }

  private static class TestCanDevice implements CanDevice {

    private final List<CanFrame> writtenFrames;
    private volatile CanFrame readFrame;
    private volatile boolean closed;
    private volatile int flushCount;

    TestCanDevice() {
      this.writtenFrames = new CopyOnWriteArrayList<>();
    }

    @Override
    public CanFrame readFrame() {
      return readFrame;
    }

    @Override
    public void writeFrame(CanFrame canFrame) throws IOException {
      writtenFrames.add(canFrame);
    }

    @Override
    public void flush() {
      flushCount++;
    }

    @Override
    public CanCapabilities getCanCapabilities() {
      return new CanCapabilities(false, false, 8, 8);
    }

    @Override
    public void close() {
      closed = true;
    }

    List<CanFrame> getWrittenFrames() {
      return writtenFrames;
    }

    void setReadFrame(CanFrame readFrame) {
      this.readFrame = readFrame;
    }

    boolean isClosed() {
      return closed;
    }

    int getFlushCount() {
      return flushCount;
    }
  }

  private static class TimingTestCanDevice extends TestCanDevice {

    private final List<Long> writeTimesNanos;

    TimingTestCanDevice() {
      this.writeTimesNanos = new CopyOnWriteArrayList<>();
    }

    @Override
    public void writeFrame(CanFrame canFrame) throws IOException {
      writeTimesNanos.add(System.nanoTime());
      super.writeFrame(canFrame);
    }

    List<Long> getWriteTimesNanos() {
      return writeTimesNanos;
    }
  }

  private static final class BlockingTestCanDevice extends TestCanDevice {

    private final Object monitor;
    private volatile boolean writesReleased;
    private volatile int writeAttempts;

    BlockingTestCanDevice() {
      this.monitor = new Object();
    }

    @Override
    public void writeFrame(CanFrame canFrame) throws IOException {
      writeAttempts++;

      synchronized (monitor) {
        while (!writesReleased) {
          try {
            monitor.wait();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while blocked", e);
          }
        }
      }

      super.writeFrame(canFrame);
    }

    int getWriteAttempts() {
      return writeAttempts;
    }

    void releaseWrites() {
      synchronized (monitor) {
        writesReleased = true;
        monitor.notifyAll();
      }
    }
  }

  private static final class FailingOnceTestCanDevice extends TestCanDevice {

    private volatile boolean failed;

    @Override
    public void writeFrame(CanFrame canFrame) throws IOException {
      if (!failed) {
        failed = true;
        throw new IOException("Synthetic write failure");
      }

      super.writeFrame(canFrame);
    }
  }

  private static final class FailingMidGroupTestCanDevice extends TestCanDevice {

    private final IOException failure = new IOException("Synthetic failure after the first frame");

    @Override
    public void writeFrame(CanFrame canFrame) throws IOException {
      if (canFrame.canIdentifier() == 11) {
        throw failure;
      }
      super.writeFrame(canFrame);
    }
  }

  private static final class AlwaysFailingTestCanDevice extends TestCanDevice {

    private final List<Long> writeAttemptTimesNanos;
    private volatile int writeAttempts;

    AlwaysFailingTestCanDevice() {
      this.writeAttemptTimesNanos = new CopyOnWriteArrayList<>();
    }

    @Override
    public void writeFrame(CanFrame canFrame) throws IOException {
      writeAttemptTimesNanos.add(System.nanoTime());
      writeAttempts++;
      throw new IOException("Synthetic persistent write failure");
    }

    int getWriteAttempts() {
      return writeAttempts;
    }

    List<Long> getWriteAttemptTimesNanos() {
      return writeAttemptTimesNanos;
    }
  }
}