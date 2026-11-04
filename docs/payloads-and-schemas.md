# Payload decoding, encoding and JSON schemas

`canbus-nmea2000` contains the shared XML-backed payload implementation for NMEA 2000 and J1939. `canbus-j1939` supplies identifier parsing/building; selecting J1939 payload definitions is a separate step in this module. Neither a PGN number nor a parsed identifier is, by itself, a decoded application message.

Start with [NmeaPayloadExample.java](../examples/java/NmeaPayloadExample.java) and [NmeaFramingExample.java](../examples/java/NmeaFramingExample.java). Both run without hardware. Use the shared [example build/run instructions](../examples/README.md).

## Choose the right boundary

| Available input/output | API | Responsibility |
| --- | --- | --- |
| Complete payload bytes plus PGN | `N2kMessageParser.decodeToJson(int, byte[])` | Decode fields; bytes exclude fast-packet headers |
| JSON envelope plus PGN | `N2kMessageParser.encodeFromJson(int, JsonObject)` | Produce complete payload bytes, not CAN frames |
| Application field-value accessor | `N2kMessageParser.encodeFromSource(int, FieldValueSource)` | Produce payload bytes from scalar accessors |
| Incoming classic CAN frames | `FrameHandler.onFrame(int, boolean, int, byte[])` | Parse identifier, assemble fast packets and decode completed payload |
| JSON plus addressing | `FramePacker.pack(int, int, int, int, JsonObject)` | Encode, build identifier and choose supported framing |
| JSON Schema for a PGN | `N2kSchemaRegistry.getSchema(int)` | Retrieve generated schema; does not execute validation |

Sources: [N2kMessageParser](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/codec/N2kMessageParser.java), [FrameHandler](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/FrameHandler.java), [FramePacker](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/FramePacker.java).

## Built-in definitions

Pass the selected compiled registry to both `new N2kMessageParser(registry)` and `new N2kSchemaRegistry(registry)`.

| Entry point | Classpath XML resource | Dialect/protocol |
| --- | --- | --- |
| `N2kParserFactory.getN2kParser()` | `n2k/canboat.xml` | CANboat / `nmea2000` |
| `N2kParserFactory.getJ1939Parser()` | `n2k/canboat-j1939.xml` | CANboat / `j1939` |
| Explicit legacy parser load | `n2k/NMEA_database_1_300.xml` | Legacy XML without `SchemaVersion` |

These are classpath resources, not configuration-directory filenames. Keep them in the deployed JAR. The factory caches separate compiled registries for its class-loader lifetime and provides no reload/reset API. For application-managed XML, use [dynamic definitions](dynamic-definitions.md).

`registry.getMessagesByPgn()` identifies exact PGNs; `getVariants(pgn)` retains compiled layouts. `getRequiredMessage(pgn)` can return `null`, despite its name, and CANboat registries may resolve a range-fallback representative. A non-null result does not prove exact field coverage. `registry.getDatabase().variants(pgn)` distinguishes exact CANboat variants; `getDatabase()` is null for legacy registries.

Sources: [N2kParserFactory](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/N2kParserFactory.java), [N2kCompiledRegistry](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/compile/N2kCompiledRegistry.java), [CanboatDatabase](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatDatabase.java).

## A reproducible payload

The payload example uses pinned independent conformance vector `pgn-unit-coverage:5`:

| Item | Value |
| --- | --- |
| PGN / variant | `129027` / `positionDeltaRapidUpdate` |
| Complete payload hex | `0164407e058003f5` |
| `packet.sid` | `1` |
| `packet.timeDelta` | `0.5` |
| `packet.latitudeDelta` | `0.001` |
| `packet.longitudeDelta` | `-0.002` |

Expected decoded envelope:

```json
{
  "packet": {"sid": 1, "timeDelta": 0.5, "latitudeDelta": 0.001, "longitudeDelta": -0.002},
  "name": "positionDeltaRapidUpdate"
}
```

The codec does not add PGN, addresses or timestamps. Retain these in application metadata or obtain addressing from `KnownMessage.getCanId()`. The example independently constructs expected values and checks encoded bytes against the literal vector.

It also decodes J1939 PGN `65008`, payload `9001e60000192000`, with the J1939 registry: line-to-line RMS voltage `400`, line-to-neutral RMS voltage `230`, frequency `50`, current `32`. The same PGN number can appear in different databases; choose the protocol intentionally.

Provenance/tolerances are in [canboat-vectors.json](../canbus-nmea2000/src/test/resources/conformance/canboat-vectors.json), [conformance notes](../canbus-nmea2000/src/test/resources/conformance/README.md) and [CanboatConformanceVectorsTest](../canbus-nmea2000/src/test/java/io/mapsmessaging/canbus/j1939/n2k/CanboatConformanceVectorsTest.java). These vectors establish evidence for their bytes/fields, not universal protocol conformance.

## JSON values and variants

Pass PGN as the method argument. Encode fields inside `packet`; optional `name` selects a CANboat layout. A JSON `pgn` property does not select the method's PGN. Preserve the decoded `name` when re-encoding messages with multiple variants.

Decode matches wire discriminators, resolving multiple matching layouts in source order. Encode throws for ambiguous layouts without sufficient name/discriminators, and for unknown/conflicting names. Supply metadata-defined references and discriminators for conditional fields.

| Field family | Representation |
| --- | --- |
| Scaled numbers | Physical numeric values after resolution/offset; encode quantizes to wire values |
| `LOOKUP`, `INDIRECT_LOOKUP`, `BITLOOKUP` | Numeric values/masks, not automatically substituted labels |
| Declared unavailable/error markers | JSON `null` for matching sentinel metadata |
| Fixed/LAU/LZ strings | Text using field encoding/length rules |
| `BINARY` | Base64 text |
| `MMSI` / `DECIMAL` | Text retaining identifier/decimal conventions |
| Repeating sets | Row objects in `packet.list` and `packet.list2` |
| Range fallback | Base64 raw payload in `packet.data` |

Lookup/dynamic-field metadata is accessible through `database.lookup(tableName, key)`. Obtain table names from XML field metadata; results are metadata maps, not a generic label-value conversion service. Keep exact integer precision when bridging 64-bit masks through consumers that represent JSON numbers as doubles.

`FieldValueSource` supplies `has`, `getLong`, `getDouble` and `getString`. The current CANboat adapter collects scalar fields into a packet without an explicit variant name. For named variants, repeated row objects and structured values, use `encodeFromJson` rather than assuming the scalar accessor covers every layout.

Sources: [CanboatCodec](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatCodec.java), [CanboatMessageSelector](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatMessageSelector.java), [CanboatValueCodec](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatValueCodec.java), [FieldValueSource](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/codec/FieldValueSource.java).

## Frame completion

The framing example supplies three strings for PGN `126998` (`configurationInformation`), packs them with `pack(pgn, 6, 42, 255, envelope)` and feeds each resulting `CanFrame` into a matching handler:

```java
Optional<Message> result = handler.onFrame(
    frame.canIdentifier(), frame.extendedFrame(), frame.dataLengthCode(), frame.data());
```

Pass identifier bits without SocketCAN flags. This path expects extended 29-bit identifiers and classic-CAN data lengths up to eight bytes. Device-layer CAN FD support does not extend the N2K handler's framing rules.

| Result | Meaning | Application response |
| --- | --- | --- |
| Empty optional | Incomplete fast packet, or ignored input such as null data/non-positive DLC | Continue receiving; do not fabricate a completed event |
| `KnownMessage` | Complete payload decoded | Inspect payload, JSON and addressing; apply domain validation |
| `UnknownMessage` | Classified invalid/unsupported/decode-failed input | Preserve reason, detail, identifier and bytes for diagnostic/fallback handling |

`KnownMessage` can contain a raw CANboat range-fallback envelope. Inspect selected name and coverage before interpreting it as typed telemetry. `UnknownReason` includes `NOT_EXTENDED_FRAME`, `INVALID_IDENTIFIER`, `INVALID_FRAME`, `UNSUPPORTED_PGN` and `DECODE_FAILED`. Retain an application exception boundary: some metadata/transport errors occur outside the final decode catch, so not every invalid input is guaranteed to become `UnknownMessage`.

Sources: [FrameHandler](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/FrameHandler.java), [KnownMessage](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/message/KnownMessage.java), [UnknownMessage](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/message/UnknownMessage.java).

## Transport and size limits

| Registry transport | Outbound behavior |
| --- | --- |
| `Single` / `single_frame` | Payload at most eight bytes; rejects oversized payload |
| `Fast` / `fast_packet` | N2K fragmentation: six payload bytes in frame zero, seven per continuation |
| `ISO` / `transport_protocol` | `FramePacker.pack` throws `UnsupportedOperationException`; J1939 TP is not installed |
| Missing metadata | Only short single-frame fallback; unknown long payloads are not assigned an invented transport |

Fast packet uses a three-bit sequence and five-bit frame index. Indices `0..31` support at most `6 + 31 × 7 = 223` payload bytes; 224 bytes throws. The packer emits full eight-byte frames and zero-initializes unused bytes. `Fast` metadata applies even to short application payloads.

`SequenceProvider` increments once per packed message, wraps after seven and scopes counters to PGN/source/destination. Each default packer has an independent provider. Share a provider where multiple packers need coordinated counters; this does not coordinate independent processes/devices.

Low-level `packFastPacket(pgn, canIdentifier, source, destination, payload)` bypasses high-level encoding/transport selection and trusts more caller input. Prefer `pack` for schema-backed messages. Enforce a non-empty payload and size bounds before transmission; a codec-accepted payload can still be too large or use an unsupported transport.

The current assembler keys state by PGN/source/destination/sequence, but does not validate continuation order, detect duplicate frame indices or expire abandoned assemblies. Its map is unsynchronized. Feed a handler an ordered, serialized stream and define application session/resource limits. Recreating a handler discards incomplete state. Do not claim loss/reordering detection or unrestricted concurrent use.

Sources: [N2kCompiledRegistry](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/compile/N2kCompiledRegistry.java), [FramePacker](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/FramePacker.java), [SequenceProvider](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/SequenceProvider.java), [FastPacketAssembler](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/framing/assemblers/FastPacketAssembler.java), [boundary tests](../canbus-nmea2000/src/test/java/io/mapsmessaging/canbus/j1939/n2k/framing/FastPacketBoundaryTest.java).

## Unknown PGNs and partial data

With no exact layout or CANboat range fallback, decode returns `null` and encode returns an empty array. Reject unexpected empty encode results before device submission. A range fallback instead preserves raw bytes as Base64 plus a fallback name; migration tests demonstrate this for PGN `130999`. Behavior depends on loaded metadata.

CANboat decode checks message minimum length; traversal can stop at a permitted partial payload, so not every possible field must be present. Truncated strings/repeat records can throw. Encoding can reject wire-range overflow, oversized strings, ambiguous variants and declared-length overflow. Quantization, reserved bits and sentinel handling mean arbitrary decode→encode is not a guarantee of bit-for-bit preservation. Test independent fixtures for the fields you need.

Sources: [migration tests](../canbus-nmea2000/src/test/java/io/mapsmessaging/canbus/j1939/n2k/CanboatMigrationTest.java), [CanboatCodec](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatCodec.java).

## Schema lookup and validation

Use the same compiled registry for parser and schema service. `listPgns()` lists exact keys; `getSchemas()` returns PGN-sorted schemas; `getSchema(pgn)` throws if no exact schema exists. Decoding via range fallback does not guarantee an exact schema is available for that requested PGN.

CANboat schemas describe `packet`/`name`, retain variant alternatives/repeat arrays and declare draft 2020-12. They include a PGN const property, while the decoder leaves PGN separate. They allow nullable scalar values and omit some field requirements for conditional/truncated payloads. They do not express every wire rule or validate identifiers, transport sessions, timestamps or device safety.

Generation is separate from validation. `getSchema` does not validate JSON; successful encode is not a full schema check. Supply a compatible validator and your desired application envelope contract. Test integer precision, variant `oneOf` selection and missing/null fields in that integration.

Legacy `N2kSchemaRegistry` and `N2kJsonSchemaGenerator.generateSchema(N2kMessageDefinition)` have different contracts: they require `pgn` and omit decoder-added `name` while forbidding additional properties. Raw legacy decoded envelopes therefore cannot simply be assumed to conform; choose/normalize the validation envelope explicitly. The single-definition generator is not a substitute for the canonical variant-aware registry.

Schema getters return cached mutable Gson objects. Call `deepCopy()` before adapting a schema and treat published registry/model objects as read-only.

Sources: [N2kSchemaRegistry](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/schema/N2kSchemaRegistry.java), [CanboatSchemas](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatSchemas.java), [N2kJsonSchemaGenerator](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/schema/N2kJsonSchemaGenerator.java).
