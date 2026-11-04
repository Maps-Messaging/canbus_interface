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
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiler;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.canbus.j1939.n2k.parser.N2kXmlDialectParser;
import io.mapsmessaging.canbus.j1939.n2k.schema.N2kSchemaRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Application-owned reload example; it does not modify the cached N2kParserFactory registries. */
public final class XmlReloadExample {
  private static final int PGN = 130816;
  private static final byte[] PAYLOAD = HexFormat.of().parseHex("d204");

  private XmlReloadExample() {}

  public static void main(String[] arguments) throws IOException, SAXException, ParserConfigurationException {
    Path path = arguments.length == 0 ? Path.of("examples/config/custom-canboat.xml") : Path.of(arguments[0]);
    RuntimeDefinitions first = prepare(N2kXmlDialectParser.parseFromFile(path), 123.4);
    AtomicReference<RuntimeDefinitions> active = new AtomicReference<>(first);
    System.out.println("File definition: " + decode(active.get()));

    // The replacement deliberately changes interpretation of the same raw value. Real changes need version governance.
    String originalXml = Files.readString(path, StandardCharsets.UTF_8);
    String replacementXml = originalXml.replace("<Resolution>0.1</Resolution>", "<Resolution>0.01</Resolution>");
    if (originalXml.equals(replacementXml)) {
      throw new IllegalArgumentException("The example requires the supplied demo fixture with Resolution 0.1");
    }
    RuntimeDefinitions replacement;
    try (InputStream stream = new ByteArrayInputStream(replacementXml.getBytes(StandardCharsets.UTF_8))) {
      replacement = prepare(N2kXmlDialectParser.parse(stream), 12.34);
    }
    // Publish the registry/parser/schema bundle only after all application checks succeed.
    active.set(replacement);
    System.out.println("Stream replacement: " + decode(active.get()));
    requireValue(first.parser(), 123.4); // An existing reader retains the old definitions.

    RuntimeDefinitions beforeFailure = active.get();
    String incompatibleXml = replacementXml.replace("<SchemaVersion>2.8.0</SchemaVersion>", "<SchemaVersion>9.0.0</SchemaVersion>");
    try (InputStream stream = new ByteArrayInputStream(incompatibleXml.getBytes(StandardCharsets.UTF_8))) {
      RuntimeDefinitions incompatible = prepare(N2kXmlDialectParser.parse(stream), 12.34);
      active.set(incompatible);
      throw new IllegalStateException("Incompatible XML unexpectedly passed validation");
    } catch (IllegalArgumentException expected) {
      if (!expected.getMessage().startsWith("Unsupported CANboat schema version:")) {
        throw expected;
      }
      System.out.println("Incompatible replacement rejected; previous bundle retained");
    }
    if (active.get() != beforeFailure) {
      throw new IllegalStateException("A rejected reload changed the active bundle");
    }
    requireValue(active.get().parser(), 12.34);
  }

  private static RuntimeDefinitions prepare(List<N2kMessageDefinition> definitions, double expectedValue) {
    // Preserve the parser-returned CanboatDefinitions list: copying it to ArrayList loses CANboat metadata.
    N2kCompiledRegistry registry = N2kCompiler.compile(definitions);
    if (registry.getDatabase() == null || !"nmea2000".equals(registry.getDatabase().getProtocol()) || registry.getMessagesByPgn().get(PGN) == null) {
      throw new IllegalArgumentException("Expected a CANboat NMEA 2000 definition for demo PGN " + PGN);
    }
    N2kMessageParser parser = new N2kMessageParser(registry);
    N2kSchemaRegistry schemas = new N2kSchemaRegistry(registry);
    schemas.getSchema(PGN); // Build/check the corresponding schema before publishing.
    requireValue(parser, expectedValue);
    return new RuntimeDefinitions(registry, parser, schemas);
  }

  private static void requireValue(N2kMessageParser parser, double expected) {
    JsonObject decoded = parser.decodeToJson(PGN, PAYLOAD);
    if (!"demoMeasurement".equals(decoded.get("name").getAsString())
        || Math.abs(decoded.getAsJsonObject("packet").get("value").getAsDouble() - expected) > 1e-9
        || !Arrays.equals(PAYLOAD, parser.encodeFromJson(PGN, decoded))) {
      throw new IllegalStateException("Demo definition/value/round-trip check failed: " + decoded);
    }
  }

  private static JsonObject decode(RuntimeDefinitions definitions) {
    return definitions.parser().decodeToJson(PGN, PAYLOAD);
  }

  // Readers obtain one bundle once per operation. AtomicReference alone does not make every contained object immutable.
  private record RuntimeDefinitions(N2kCompiledRegistry registry, N2kMessageParser parser, N2kSchemaRegistry schemas) {}
}
