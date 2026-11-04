# CANaerospace event decoding

`canbus-events` loads CANaerospace YAML definitions and decodes eight-byte payloads into `ParsedCanaerospaceMessage`
objects. It provides schema/identifier metadata and `DataTypeCodec` for the four-byte data field. It does not open
interfaces, publish topics or schedule transmissions; the host application connects it to device and messaging APIs.

The Maven artifact is `io.mapsmessaging:canbus-events`, using the version selected for this repository. Runtime
dependencies include SnakeYAML and Gson; Lombok is used at build time. Java 21 is the repository compilation target.

A minimal startup/decode sequence is:

```java
import io.mapsmessaging.canbus.canaerospace.parser.CanaerospaceFrameParser;
import io.mapsmessaging.canbus.canaerospace.parser.ParsedCanaerospaceMessage;
import io.mapsmessaging.canbus.canaerospace.schema.CanaerospaceSchemaRegistry;

CanaerospaceSchemaRegistry registry = CanaerospaceSchemaRegistry.loadFromClasspath();
CanaerospaceFrameParser parser = new CanaerospaceFrameParser(registry);
byte[] payload = {42, 6, 0, 1, 0, 0, 3, (byte) 0xE8};
ParsedCanaerospaceMessage decoded = parser.parse(300, payload);
```

The bundled definition makes ID300 a `SHORT` body longitudinal acceleration, with raw value `1000` and engineering
value approximately `0.488 g`. The first four payload bytes contain node ID, type number, service code and message
code; the last four contain the typed data. `DataTypeCodec.encode("SHORT", 1000)` builds those four data bytes, not an
entire CAN frame. A host supplies the header, standard CAN identifier and transmission policy.

Use `CanaerospaceSchemaRegistry.load(Path)` for a trusted custom YAML file, then construct a new parser. Runtime
replacement is a host operation; editing the file does not reload existing registries. Schema type definitions do not
add codec implementations, and schema ranges/layout declarations are not general runtime validation rules.

Read [CANaerospace and identifier integration](../docs/canaerospace-and-identifiers.md) for a complete custom YAML,
startup/update workflow, codec offsets, unknown-type behavior, JSON output and native-format limitations. Run the
hardware-free [CanaerospaceExample](../examples/java/CanaerospaceExample.java) using the shared
[example instructions](../examples/README.md).

The native-format channel, node-service and time-slot tables in `canaerospace_schema.yaml` are metadata; this module
does not implement those channel protocols, exchanges or timers. JSON from Gson is decoded DTO/metadata output,
not generated JSON Schema. NMEA 2000/J1939 XML payload definitions are handled by
[canbus-nmea2000](../canbus-nmea2000/README.md), rather than this CANaerospace module.

Licensed under Apache License 2.0 with the Commons Clause; see the repository [LICENSE](../LICENSE).
