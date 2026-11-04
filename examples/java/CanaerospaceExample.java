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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.mapsmessaging.canbus.canaerospace.parser.CanaerospaceFrameParser;
import io.mapsmessaging.canbus.canaerospace.parser.DataTypeCodec;
import io.mapsmessaging.canbus.canaerospace.parser.ParsedCanaerospaceMessage;
import io.mapsmessaging.canbus.canaerospace.schema.CanaerospaceSchemaRegistry;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierDefinition;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

/** Encodes and parses one fixed-format CANaerospace frame without opening a CAN device. */
public final class CanaerospaceExample {

  private CanaerospaceExample() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 1) {
      throw new IllegalArgumentException("Usage: CanaerospaceExample [custom-schema.yaml]");
    }
    CanaerospaceSchemaRegistry registry = args.length == 0
        ? CanaerospaceSchemaRegistry.loadFromClasspath()
        : CanaerospaceSchemaRegistry.load(Path.of(args[0]));
    IdentifierDefinition definition = registry.findIdentifier(300)
        .orElseThrow(() -> new IllegalArgumentException("Example requires identifier 300"));
    if (!"SHORT".equals(definition.getDataType())
        || !"SHORT".equals(registry.findDataTypeNameByNumber(6).orElse(null))) {
      throw new IllegalArgumentException("Example requires identifier 300 and payload type 6 to use SHORT");
    }

    byte[] data = DataTypeCodec.encode("SHORT", 1000);
    byte[] payload = new byte[8];
    payload[0] = 42; // Node ID.
    payload[1] = 6; // SHORT in the bundled schema.
    payload[2] = 0; // Service code chosen by this example.
    payload[3] = 1; // Message code chosen by this example.
    System.arraycopy(data, 0, payload, 4, data.length);

    CanaerospaceFrameParser parser = new CanaerospaceFrameParser(registry);
    ParsedCanaerospaceMessage parsed = parser.parse(300, payload);
    if (parsed.isDataTypeMismatch() || !Integer.valueOf(1000).equals(parsed.getRawValue())) {
      throw new IllegalStateException("Unexpected decode result");
    }
    byte[] encodedAgain = DataTypeCodec.encode(parsed.getSchemaDataType(), parsed.getRawValue());
    if (!Arrays.equals(data, encodedAgain)) {
      throw new IllegalStateException("Data field round trip changed its bytes");
    }

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    System.out.println("Payload hex: " + HexFormat.of().withUpperCase().formatHex(payload));
    System.out.println("Identifier metadata (not JSON Schema):");
    System.out.println(gson.toJson(definition));
    System.out.println("Decoded message:");
    System.out.println(gson.toJson(parsed));
  }
}
