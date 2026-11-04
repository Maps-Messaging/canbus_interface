# NMEA 2000 and J1939 payloads

This module parses public CANboat XML, compiles PGN definitions and encodes/decodes complete payloads as Gson JSON. It includes separate packaged NMEA 2000 and J1939 databases, variant/lookup metadata, JSON Schema generation and classic-CAN single/fast-packet framing helpers.

Raw device access belongs to `canbus-core`. Identifier parsing/building belongs to `canbus-J1939`. J1939 payload codecs are supplied here through the shared CANboat implementation; an identifier parser alone does not decode SPNs or install transport sessions.

## Start here

| Guide/example | Coverage |
| --- | --- |
| [Payloads and schemas](../docs/payloads-and-schemas.md) | API boundaries, JSON fields, variants, fallback, schemas and framing limits |
| [Dynamic definitions](../docs/dynamic-definitions.md) | XML file/stream loading, compilation and application-owned replacement |
| [Build/run examples](../examples/README.md) | Shared executable instructions |
| [NmeaPayloadExample.java](../examples/java/NmeaPayloadExample.java) | Independent N2K byte vector and shared J1939 payload decoding |
| [NmeaFramingExample.java](../examples/java/NmeaFramingExample.java) | Pack PGN 126998, reassemble and check decoded strings without hardware |
| [XmlReloadExample.java](../examples/java/XmlReloadExample.java) | File/stream loading, candidate checks, atomic publication and rejected reload |

## Core APIs

```java
N2kCompiledRegistry registry = N2kParserFactory.getN2kParser();
N2kMessageParser parser = new N2kMessageParser(registry);
JsonObject envelope = parser.decodeToJson(pgn, completePayload);
byte[] encoded = parser.encodeFromJson(pgn, envelope);
N2kSchemaRegistry schemas = new N2kSchemaRegistry(registry);
JsonObject schema = schemas.getSchema(pgn).deepCopy();
```

This excerpt shows the sequence; linked complete examples include imports, checked exceptions, known PGNs and expected byte/value checks. Select `N2kParserFactory.getJ1939Parser()` for J1939. Both factories cache registries; neither watches or reloads XML files.

| Packaged resource | Purpose |
| --- | --- |
| `n2k/canboat.xml` | Default CANboat NMEA 2000 database |
| `n2k/canboat-j1939.xml` | CANboat J1939 database |
| `n2k/NMEA_database_1_300.xml` | Explicitly loadable legacy dialect |

CANboat decode returns `{"packet": {...}, "name": "variantId"}`. PGN is a separate argument; addresses/timestamps are not added to JSON. Encode uses `packet` and optional `name` for layout selection. Repeated sets use `list`/`list2`; binary/raw fallback uses Base64. Lookup values remain numeric rather than automatically becoming labels.

## Integration limits

- `N2kMessageParser` consumes complete payloads without fast-packet headers. Use a matching `FrameHandler` for CAN frames.
- `FramePacker.pack` supports classic-CAN single frames and N2K fast packets up to 223 payload bytes. It rejects `ISO`/J1939 transport-protocol output; it does not implement BAM/RTS/CTS sessions.
- Preserve unknown/error messages and raw fallback instead of treating every decoded wrapper as complete semantic coverage.
- Schema generation does not run a validator or express every wire/transport rule. Canonical and legacy schema contracts differ.
- Runtime XML requires explicit application preparation/verification/publication. Preserve `CanboatDefinitions` metadata when compiling; ordinary list copying changes compiler selection.
- Assemblers have state and ordering/expiry/concurrency limits; read the framing guide before integrating concurrent streams or live reload.

The [pinned conformance corpus](src/test/resources/conformance/README.md) and [migration tests](src/test/java/io/mapsmessaging/canbus/j1939/n2k/CanboatMigrationTest.java) provide concrete byte/value evidence. They do not establish compatibility with every proprietary PGN, upstream revision or device.

See the [repository overview](../README.md) for module selection and device integration.
