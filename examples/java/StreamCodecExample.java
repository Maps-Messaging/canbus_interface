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

import io.mapsmessaging.canbus.device.codec.CanFrameStreamCodec;
import io.mapsmessaging.canbus.device.codec.SlcanCanFrameStreamCodec;
import io.mapsmessaging.canbus.device.codec.impl.WaveshareUsbCanAStreamCodec;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HexFormat;

/** Hardware-free classic CAN framing round trips using both bundled stream codecs. */
public final class StreamCodecExample {

  private StreamCodecExample() {
  }

  public static void main(String[] arguments) throws IOException {
    CanFrame standard = new CanFrame(0x123, false, 3, new byte[] {0x01, 0x23, (byte) 0xAB});
    CanFrame extended = new CanFrame(0x18FEF100, true, 2, new byte[] {0x45, 0x67});
    roundTrip("SLCAN", new SlcanCanFrameStreamCodec(), standard, extended);
    roundTrip("Waveshare USB-CAN-A", new WaveshareUsbCanAStreamCodec(), standard, extended);
  }

  private static void roundTrip(String label, CanFrameStreamCodec codec, CanFrame first, CanFrame second) throws IOException {
    byte[] wireBytes;
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      codec.write(output, first);
      codec.write(output, second);
      codec.flush(output);
      wireBytes = output.toByteArray();
    }
    try (ByteArrayInputStream input = new ByteArrayInputStream(wireBytes)) {
      CanFrame decodedFirst = codec.read(input);
      CanFrame decodedSecond = codec.read(input);
      if (!first.equals(decodedFirst) || !second.equals(decodedSecond)) {
        throw new IOException(label + " frame round trip failed");
      }
      System.out.println(label + ": " + HexFormat.of().formatHex(wireBytes));
      System.out.println("  " + decodedFirst);
      System.out.println("  " + decodedSecond);
    }
  }
}
