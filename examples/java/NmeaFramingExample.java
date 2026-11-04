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

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import io.mapsmessaging.canbus.j1939.n2k.N2kParserFactory;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.framing.FrameHandler;
import io.mapsmessaging.canbus.j1939.n2k.framing.FramePacker;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.KnownMessage;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.Message;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.UnknownMessage;
import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** No hardware: encode a built-in fast-packet PGN, split it into classic CAN frames and reassemble it. */
public final class NmeaFramingExample {
  private NmeaFramingExample() {}

  public static void main(String[] arguments) throws IOException, SAXException, ParserConfigurationException {
    int pgn = 126998;
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    JsonObject envelope = new JsonObject();
    JsonObject packet = new JsonObject();
    packet.addProperty("installationDescription1", "Bridge example");
    packet.addProperty("installationDescription2", "Dock");
    packet.addProperty("manufacturerInformation", "Demo only");
    envelope.addProperty("name", "configurationInformation");
    envelope.add("packet", packet);
    byte[] payload = parser.encodeFromJson(pgn, envelope);

    FramePacker packer = new FramePacker(parser);
    List<CanFrame> frames = packer.pack(pgn, 6, 42, 255, envelope);
    if (frames.size() < 2) {
      throw new IllegalStateException("The example must exercise a multi-frame fast packet");
    }
    FrameHandler handler = new FrameHandler(parser);
    KnownMessage complete = null;
    for (int index = 0; index < frames.size(); index++) {
      CanFrame frame = frames.get(index);
      System.out.println("Frame " + index + ": " + HexFormat.of().formatHex(frame.data()));
      Optional<Message> result = handler.onFrame(frame.canIdentifier(), frame.extendedFrame(), frame.dataLengthCode(), frame.data());
      if (result.isEmpty()) {
        if (index == frames.size() - 1) {
          throw new IllegalStateException("Final frame did not complete the message");
        }
        continue;
      }
      Message message = result.orElseThrow();
      if (message instanceof UnknownMessage unknown) {
        throw new IllegalStateException(unknown.getReason() + ": " + unknown.getDetail());
      }
      if (!(message instanceof KnownMessage known) || index != frames.size() - 1) {
        throw new IllegalStateException("Unexpected message or premature completion");
      }
      complete = known;
    }
    if (complete == null || !Arrays.equals(payload, complete.getPayload())) {
      throw new IllegalStateException("Reassembled payload differs from the encoded payload");
    }
    JsonObject decoded = complete.getDecoded();
    if (!"configurationInformation".equals(decoded.get("name").getAsString()) || !packet.equals(decoded.getAsJsonObject("packet"))) {
      throw new IllegalStateException("Reassembled JSON differs from the supplied strings: " + decoded);
    }
    System.out.println("Reassembled " + payload.length + " payload bytes from " + frames.size() + " frames: " + decoded);
  }
}
