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
import io.mapsmessaging.canbus.j1939.n2k.N2kParserFactory;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.schema.N2kSchemaRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** No hardware: decode and encode independently specified payload vectors from the repository's conformance corpus. */
public final class NmeaPayloadExample {
  private NmeaPayloadExample() {}

  public static void main(String[] arguments) throws IOException, SAXException, ParserConfigurationException {
    N2kCompiledRegistry registry = N2kParserFactory.getN2kParser();
    N2kMessageParser parser = new N2kMessageParser(registry);
    byte[] expected = HexFormat.of().parseHex("0164407e058003f5");
    JsonObject decoded = parser.decodeToJson(129027, expected);
    JsonObject packet = decoded.getAsJsonObject("packet");
    requireNumber(packet, "sid", 1, 0);
    requireNumber(packet, "timeDelta", 0.5, 0.0005);
    requireNumber(packet, "latitudeDelta", 0.001, 5e-12);
    requireNumber(packet, "longitudeDelta", -0.002, 5e-12);
    System.out.println("NMEA 2000 PGN 129027: " + decoded);

    JsonObject input = new JsonObject();
    JsonObject values = new JsonObject();
    values.addProperty("sid", 1);
    values.addProperty("timeDelta", 0.5);
    values.addProperty("latitudeDelta", 0.001);
    values.addProperty("longitudeDelta", -0.002);
    input.addProperty("name", "positionDeltaRapidUpdate");
    input.add("packet", values);
    if (!Arrays.equals(expected, parser.encodeFromJson(129027, input))) {
      throw new IllegalStateException("PGN 129027 encoded bytes differ from the independent vector");
    }
    N2kSchemaRegistry schemas = new N2kSchemaRegistry(registry);
    JsonObject schema = schemas.getSchema(129027).deepCopy();
    System.out.println("Schema: " + schema.get("$id").getAsString());
    // Retrieving a schema does not validate JSON; an application may supply its chosen JSON Schema validator.

    N2kMessageParser j1939 = new N2kMessageParser(N2kParserFactory.getJ1939Parser());
    byte[] acPayload = HexFormat.of().parseHex("9001e60000192000");
    JsonObject ac = j1939.decodeToJson(65008, acPayload);
    requireNumber(ac.getAsJsonObject("packet"), "lineLineAcRmsVoltage", 400, 0);
    requireNumber(ac.getAsJsonObject("packet"), "acFrequency", 50, 5e-6);
    if (!Arrays.equals(acPayload, j1939.encodeFromJson(65008, ac))) {
      throw new IllegalStateException("J1939 PGN 65008 payload round trip failed");
    }
    System.out.println("J1939 PGN 65008 through the shared payload module: " + ac);
  }

  private static void requireNumber(JsonObject packet, String key, double expected, double tolerance) {
    if (!packet.has(key) || packet.get(key).isJsonNull() || Math.abs(packet.get(key).getAsDouble() - expected) > tolerance) {
      throw new IllegalStateException("Unexpected " + key + ": " + packet.get(key));
    }
  }
}
