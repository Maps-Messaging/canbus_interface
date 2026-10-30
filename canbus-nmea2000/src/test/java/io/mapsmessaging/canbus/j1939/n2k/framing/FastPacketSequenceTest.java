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
package io.mapsmessaging.canbus.j1939.n2k.framing;

import io.mapsmessaging.canbus.device.frames.CanFrame;
import io.mapsmessaging.canbus.j1939.CanId;
import io.mapsmessaging.canbus.j1939.CanIdBuilder;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.framing.assemblers.FastPacketAssembler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MSG-207: sequence IDs are scoped to each provider's PGN/source/destination tuple.
 * Reuse after eight messages is expected; concurrent uniqueness is bounded to one cycle.
 */
class FastPacketSequenceTest {

  private static final int PGN = 126208;
  private static final int SOURCE = 42;
  private static final int DESTINATION = 77;
  private static final byte[] PAYLOAD = {
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
  };

  @Test
  void consecutive_messages_advance_sequence_once_and_wrap_after_seven() {
    FramePacker packer = packer();
    int[] expectedSequences = {0, 1, 2, 3, 4, 5, 6, 7, 0, 1};
    for (int expected : expectedSequences) {
      List<CanFrame> frames = pack(packer, PGN, SOURCE, DESTINATION, PAYLOAD);
      assertPacket(frames, expected, PAYLOAD);
    }
  }

  @Test
  void short_fast_packet_consumes_one_sequence_id() {
    FramePacker packer = packer();
    byte[] shortPayload = {21, 22, 23};

    List<CanFrame> first = pack(packer, PGN, SOURCE, DESTINATION, shortPayload);
    List<CanFrame> second = pack(packer, PGN, SOURCE, DESTINATION, PAYLOAD);

    assertEquals(1, first.size());
    assertPacket(first, 0, shortPayload);
    assertPacket(second, 1, PAYLOAD);
  }

  @Test
  void pgn_source_and_destination_each_have_independent_sequences() {
    FramePacker packer = packer();
    // Both PGNs are PDU1: differing destinations are present in the wire identifier.
    int[][] tuples = {{PGN, SOURCE, DESTINATION}, {126720, SOURCE, DESTINATION},
        {PGN, 43, DESTINATION}, {PGN, SOURCE, 78}};
    for (int[] tuple : tuples) {
      assertPacket(pack(packer, tuple[0], tuple[1], tuple[2], PAYLOAD), 0, PAYLOAD);
    }
    for (int[] tuple : tuples) {
      assertPacket(pack(packer, tuple[0], tuple[1], tuple[2], PAYLOAD), 1, PAYLOAD);
    }
  }

  @Test
  void packers_sharing_a_provider_share_sequence_allocation() {
    SequenceProvider provider = new SequenceProvider();
    N2kMessageParser parser = new N2kMessageParser(new N2kCompiledRegistry(Map.of()));
    FramePacker first = new FramePacker(parser, provider);
    FramePacker second = new FramePacker(parser, provider);

    assertPacket(pack(first, PGN, SOURCE, DESTINATION, PAYLOAD), 0, PAYLOAD);
    assertPacket(pack(second, PGN, SOURCE, DESTINATION, PAYLOAD), 1, PAYLOAD);
    assertPacket(pack(first, PGN, SOURCE, DESTINATION, PAYLOAD), 2, PAYLOAD);
  }

  @Test
  void default_packer_instances_have_independent_sequence_providers() {
    FramePacker first = packer();
    FramePacker second = packer();

    assertPacket(pack(first, PGN, SOURCE, DESTINATION, PAYLOAD), 0, PAYLOAD);
    assertPacket(pack(first, PGN, SOURCE, DESTINATION, PAYLOAD), 1, PAYLOAD);
    assertPacket(pack(second, PGN, SOURCE, DESTINATION, PAYLOAD), 0, PAYLOAD);
  }

  @Test
  void interleaved_messages_reassemble_across_sequence_wrap() {
    FramePacker packer = packer();
    for (int sequence = 0; sequence < 7; sequence++) {
      pack(packer, PGN, SOURCE, DESTINATION, PAYLOAD);
    }
    byte[] otherPayload = PAYLOAD.clone();
    otherPayload[0] = 99;
    List<CanFrame> beforeWrap = pack(packer, PGN, SOURCE, DESTINATION, PAYLOAD);
    List<CanFrame> afterWrap = pack(packer, PGN, SOURCE, DESTINATION, otherPayload);
    assertPacket(beforeWrap, 7, PAYLOAD);
    assertPacket(afterWrap, 0, otherPayload);
    FastPacketAssembler assembler = new FastPacketAssembler();
    for (int index = 0; index < 3; index++) {
      byte[] first = accept(assembler, beforeWrap.get(index));
      byte[] second = accept(assembler, afterWrap.get(index));
      if (index < 2) {
        assertNull(first);
        assertNull(second);
      } else {
        assertArrayEquals(PAYLOAD, first);
        assertArrayEquals(otherPayload, second);
      }
    }
    CanId identifier = CanId.parse(beforeWrap.getFirst().canIdentifier());
    assertFalse(assembler.hasInProgress(identifier, 7));
    assertFalse(assembler.hasInProgress(identifier, 0));
  }

  @Test
  @Timeout(15)
  void concurrent_messages_allocate_each_sequence_once_per_cycle() throws Exception {
    FramePacker packer = packer();
    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      for (int cycle = 0; cycle < 3; cycle++) {
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<CanFrame>>> results = new ArrayList<>();
        try {
          for (int message = 0; message < 8; message++) {
            results.add(executor.submit(() -> {
              ready.countDown();
              assertTrue(start.await(5, TimeUnit.SECONDS), "Timed out waiting for concurrent start");
              return pack(packer, PGN, SOURCE, DESTINATION, PAYLOAD);
            }));
          }
          assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not reach the start barrier");
        } finally {
          start.countDown();
        }
        Set<Integer> sequences = new HashSet<>();
        for (Future<List<CanFrame>> result : results) {
          List<CanFrame> frames = result.get(5, TimeUnit.SECONDS);
          int sequence = (frames.getFirst().data()[0] & 0xFF) >>> 5;
          assertTrue(sequences.add(sequence), "Duplicate sequence within one eight-message cycle");
          assertPacket(frames, sequence, PAYLOAD);
        }
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), sequences);
      }
    }
  }

  private static FramePacker packer() {
    return new FramePacker(new N2kMessageParser(new N2kCompiledRegistry(Map.of())));
  }

  private static List<CanFrame> pack(FramePacker packer, int pgn, int source, int destination, byte[] payload) {
    int identifier = CanIdBuilder.build(pgn, 6, source, destination);
    return packer.packFastPacket(pgn, identifier, source, destination, payload);
  }

  private static byte[] accept(FastPacketAssembler assembler, CanFrame frame) {
    int header = frame.data()[0] & 0xFF;
    return assembler.accept(CanId.parse(frame.canIdentifier()), header >>> 5, header & 0x1F, frame.data());
  }

  private static void assertPacket(List<CanFrame> frames, int sequence, byte[] payload) {
    assertEquals(payload.length == 3 ? 1 : 3, frames.size());
    assertEquals(payload.length, frames.getFirst().data()[1] & 0xFF);
    byte[] reconstructed = new byte[payload.length];
    int offset = 0;
    for (int index = 0; index < frames.size(); index++) {
      CanFrame frame = frames.get(index);
      assertEquals(8, frame.dataLengthCode());
      assertEquals((sequence << 5) | index, frame.data()[0] & 0xFF,
          "Sequence/frame header at index " + index);
      int start = index == 0 ? 2 : 1;
      int count = Math.min(8 - start, payload.length - offset);
      System.arraycopy(frame.data(), start, reconstructed, offset, count);
      offset += count;
    }
    assertArrayEquals(payload, reconstructed);
  }
}
