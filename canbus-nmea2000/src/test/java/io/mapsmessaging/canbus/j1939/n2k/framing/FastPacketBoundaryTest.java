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
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.framing.assemblers.FastPacketAssembler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** MSG-207: all five frame-index bits remain usable, including index 31. */
class FastPacketBoundaryTest {

  private static final int PGN = 126208;
  private static final int IDENTIFIER = 0x19ED4D2A;

  @Test
  void pack_216_bytes_uses_31_frames() {
    assertBoundary(216, 31);
  }

  @Test
  void pack_217_bytes_uses_final_frame_index() {
    assertBoundary(217, 32);
  }

  @Test
  void pack_223_bytes_uses_full_32_frame_capacity() {
    assertBoundary(223, 32);
  }

  @Test
  void pack_224_bytes_rejects_frame_index_overflow() {
    FramePacker packer = packer();

    assertThrows(IllegalStateException.class,
        () -> packer.packFastPacket(PGN, IDENTIFIER, 42, 77, new byte[224]));
  }

  private static void assertBoundary(int length, int expectedFrameCount) {
    FramePacker packer = packer();
    byte[] payload = new byte[length];
    for (int index = 0; index < length; index++) {
      payload[index] = (byte) index;
    }
    int[] expectedSequences = {0, 1, 2, 3, 4, 5, 6, 7, 0};
    for (int sequence : expectedSequences) {
      List<CanFrame> frames = packer.packFastPacket(PGN, IDENTIFIER, 42, 77, payload);
      assertEquals(expectedFrameCount, frames.size());
      assertEquals(length, frames.getFirst().data()[1] & 0xFF);
      FastPacketAssembler assembler = new FastPacketAssembler();
      for (int index = 0; index < expectedFrameCount; index++) {
        CanFrame frame = frames.get(index);
        assertEquals(IDENTIFIER, frame.canIdentifier());
        assertEquals(8, frame.dataLengthCode());
        assertEquals((sequence << 5) | index, frame.data()[0] & 0xFF);
        byte[] assembled = assembler.accept(CanId.parse(frame.canIdentifier()), sequence, index, frame.data());
        if (index < expectedFrameCount - 1) {
          assertNull(assembled);
        } else {
          assertArrayEquals(payload, assembled);
        }
      }
    }
  }

  private static FramePacker packer() {
    return new FramePacker(new N2kMessageParser(new N2kCompiledRegistry(Map.of())));
  }
}
