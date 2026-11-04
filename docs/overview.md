# Integrating the Canbus libraries

The libraries separate device I/O, CAN identifiers, complete message payloads and application events. You can use the payload/identifier/schema components without native hardware or a MapsMessaging server.

This guide describes development version `1.1.0-SNAPSHOT`. The examples use a full JDK compatible with the repository's Java 21 target. Match the documentation and XML/YAML definitions to the actual artifact deployed.

## Choose the entry point

| Input or requirement | Entry point | Module |
| --- | --- | --- |
| Raw Linux CAN interface | `SocketCanDevice` through `CanDevice` | `canbus-core` |
| Adapter bytes from application-owned streams | `SerialCanDevice`, `CanFrameStreamCodec` implementations | `canbus-core` |
| Buffered outbound messages | `QueuedCanDevice` | `canbus-core` |
| J1939 29-bit identifier | `CanIdBuilder`, `CanId` | `canbus-J1939` |
| Complete NMEA/J1939 payload bytes and PGN | `N2kMessageParser` | `canbus-nmea2000` |
| Individual classic N2K CAN frames | `FrameHandler` | `canbus-nmea2000` |
| Complete payload to outbound CAN frames | `FramePacker` | `canbus-nmea2000` |
| Custom PGN XML | `N2kXmlDialectParser`, `N2kCompiler` | `canbus-nmea2000` |
| JSON schemas for decoded payloads | `N2kSchemaRegistry` | `canbus-nmea2000` |
| CANaerospace YAML and frames | `CanaerospaceSchemaRegistry`, `CanaerospaceFrameParser` | `canbus-events` |
| Classic extended-frame candump log | `CanLogToJsonDecoder` | `canbus-nmea2000` |

The parent artifact `canbus` is an aggregator, not a runtime API. Use the exact module coordinates listed in the [root README](../README.md). `canbus-events` is independently usable; it does not need a SocketCAN device to interpret a supplied CANaerospace frame.

## Typical receive path

1. Open the device/stream selected by the application, or obtain frame bytes from an existing transport.
2. Carry the identifier bits, extended-frame flag and actual payload length separately.
3. For N2K, feed individual frames into the `FrameHandler` associated with that bus and registry. An empty result can mean a fast packet is incomplete; do not treat every empty result as an unknown PGN.
4. Handle completed `KnownMessage` and diagnostic `UnknownMessage` results explicitly.
5. Route/store the decoded data in the embedding application. The library does not publish it to MQTT, a database or a UI automatically.

If the input is already a complete reassembled payload, call `N2kMessageParser.decodeToJson(pgn, payload)` directly. Do not feed a complete multi-frame payload into an API expecting one classic CAN frame.

CANaerospace has its own identifier/payload interpretation and YAML registry. Do not pass its standard 11-bit traffic to a classic N2K handler and expect the same result.

## Typical transmit path

1. Select the exact PGN variant and supply the expected JSON envelope or `FieldValueSource`.
2. Encode a complete payload through `N2kMessageParser`.
3. Use `FramePacker` to apply the supported N2K framing and J1939 identifier fields.
4. Check the chosen device's capabilities and transmit through the application's selected write/queue policy.

Encoding JSON does not transmit anything. The library does not claim an address, arbitrate application source addresses or manage a complete J1939 network. Verify that an operation is permitted and meaningful on the intended bus before writing frames.

## Runtime schema lifecycle

The default NMEA and J1939 factories cache separate compiled registries. Loading a custom XML file means explicitly constructing another registry/parser, not changing those singleton references. See [dynamic definitions](dynamic-definitions.md) for file/stream examples, version validation and application-level replacement.

Keep a compatible parser, schema view and stateful frame handler together. In-flight fast-packet state belongs to the handler; decide how to stop/drain/drop it at a definition change rather than mixing fragments decoded under different versions. Keep an old bundle usable until a candidate definition has loaded, compiled and passed the application's validation.

## Application responsibilities

| Library provides | Application supplies |
| --- | --- |
| Frame objects and device interfaces | OS configuration, stream acquisition, permissions and shutdown coordination |
| Payload codecs and schema metadata | Selection of the right protocol/definitions and validation of application-required PGNs |
| Fast-packet reassembly | Per-bus ownership, ordered processing and policy for incomplete traffic |
| CANaerospace typed parsing | Correct YAML, identifier/channel selection and semantic interpretation |
| Queueing/bandwidth estimates | Backpressure/drop policy, monitoring and safe bus traffic rates |
| Unknown/decode-failure diagnostics | Logging, counters, retention of raw data and operator response |

No dependency injection framework, network listener or server process is required for the offline examples.

## Reading order

1. [Complete examples and build commands](../examples/README.md)
2. [Payloads, variants and JSON schemas](payloads-and-schemas.md)
3. [Dynamic XML definitions and replacement](dynamic-definitions.md)
4. [Device/stream access and queued writes](devices-and-streams.md)
5. [CANaerospace and J1939 identifiers](canaerospace-and-identifiers.md)
6. [Operations and troubleshooting](operations.md)
7. [Offline tools](offline-tools.md)

The historical [migration note](canboat-migration.md) records the pinned CANboat provenance and earlier verification. Its old test totals/build limitations are historical evidence, not the result of running today's examples.
