# Operations and troubleshooting

This guide describes the current development implementation. It complements the [overview](overview.md), [device guide](devices-and-streams.md) and [payload/definition guides](payloads-and-schemas.md).

## Packaging and resource discovery

The runtime contains separate JARs and schema resources. Use Maven's declared dependencies or package all required modules deliberately; the aggregator `canbus` POM alone supplies no API classes.

| Resource | Consumer |
| --- | --- |
| `/n2k/canboat.xml` | Default NMEA payload registry |
| `/n2k/canboat-j1939.xml` | Separate J1939 payload registry in `canbus-nmea2000` |
| `/n2k/NMEA_database_1_300.xml` | Explicit legacy XML loading; not the NMEA default |
| `/canaerospace_schema.yaml` | Default CANaerospace registry |

The identifier-only J1939 module also contains a resource named `canboat-j1939.xml` at its root; that is not the resource path used by `N2kParserFactory.getJ1939Parser()`.

Keep schema files and their upstream notices when shading or distributing application JARs. Avoid accidentally overwriting same-named resources with a different schema generation. Classpath parsing uses class-loader lookup; file paths and classpath resource names are different APIs.

The core Maven build produces a shaded artifact containing its selected dependencies. Use a consistent dependency set in your application and inspect the actual packaged classpath if you add a second shading step. Native SocketCAN/JNA support does not make every protocol path dependent on Linux; the hardware-free parser examples do not open native sockets.

## Definition updates

Version your XML/YAML alongside the application configuration. Record protocol, metadata version and source/provenance, then load/compile a candidate before accepting traffic under it.

Do not equate an accepted XML syntax/schema major version with complete wire conformance for every new vendor definition. Test required PGNs, variants, sentinels, lengths, repeats and transport types against independent sample payloads. Modern CANboat and legacy XML take different codec paths.

The application's reload operation should publish a coherent bundle of registry, parser, JSON-schema view and a fresh frame handler. Keep the previous bundle on parse/compile/validation failure. Define what happens to in-progress messages at replacement time; the library does not automatically migrate reassembly state or refresh its static factories.

See [dynamic definitions](dynamic-definitions.md) for an executable example and lifecycle details.

## Concurrency and mutable values

- Give each input bus a clearly owned `FrameHandler`; its fast-packet assembler contains mutable state. Serialize access rather than assuming it is thread-safe.
- `FrameHandler` and `CanLogToJsonDecoder` are not stateless shared utilities. A decoder instance also tracks conversion state.
- Treat compiled registry models and returned JSON schemas as read-only application data. If you need to customize a JSON schema obtained from `N2kSchemaRegistry`, make a deep copy rather than modifying a shared cached object.
- `CanFrame` copies its payload at construction and when exposing `data()`. Do not infer that every lower-level native structure or protocol DTO has the same ownership contract.
- Coordinate writes and close operations through one device lifecycle. `writeFrames` calls individual writes; it is not a transaction that rolls back already transmitted frames.

## Reassembly, queues and timing

The classic N2K path can accumulate a fast packet across several CAN frames. `Optional.empty()` from the handler can mean that more fragments are needed, not that decoding succeeded or failed.

The current fast-packet assembler does not provide a general expiry scheduler, bounded retained-state policy, or complete out-of-order/duplicate-fragment validation. A missing start/continuation can leave incomplete state or prevent message completion. The application must account for dropped, reordered, stale or interleaved traffic and own handler replacement/traffic validation; do not use the implementation as a certification-grade transport validator.

`QueuedCanDevice` provides queue policies, counters and a bandwidth estimate, not a measurement of real bus occupancy. Queue acceptance is not proof that the delegate or physical bus transmitted the message. `flush`/close/drain semantics and failure counters are detailed in the [device guide](devices-and-streams.md). Source address selection, bus bitrate, retries and application delivery acknowledgements remain outside this queue.

## Identifier, length and numeric boundaries

- Keep raw identifier bits separate from the extended-frame flag. Native SocketCAN flags are not PGN bits.
- In `CanFrame`, `dataLengthCode` is used as a byte count from 0 to 64, not a CAN FD encoded-DLC-to-length conversion API. The current frame model infers FD from a payload longer than eight bytes; short FD-frame metadata is not represented independently.
- A core frame's ability to hold 64 bytes does not enable FD for a socket/adapter or make `FrameHandler`/`FramePacker` support FD N2K.
- A zero-length core frame is legal, but the N2K handler currently returns no result for null data or a non-positive length. Use the layer-specific contract.
- Preserve unsigned integer precision. CANboat ISO NAME values can exceed the exact integer range of an IEEE-754 double; do not coerce every JSON number through `double`/JavaScript `Number` before re-encoding.
- Missing/unavailable numeric values can be JSON null. A schema, an absent field and an explicit null are not interchangeable application meanings.

## Encoding and schema caveats

CANboat string encodings and their metadata differ from the CANaerospace typed codec. The current CANaerospace ACHAR4 decoder constructs a string with the platform default charset, while its encoder uses ISO-8859-1. Do not promise portable arbitrary-text round trips through that type without testing the actual bytes and runtime.

CANaerospace YAML includes illustrative metadata such as time-slot examples. Those examples do not override the actual identifier/data-type definitions loaded by the registry. In the bundled YAML, ID 300/301 time-slot examples and identifier definitions use different type labels; use the authoritative identifier definition when parsing actual traffic. See [CANaerospace](canaerospace-and-identifiers.md).

The JSON-schema API describes decoded data. It does not automatically reject an application request, validate a received frame or transmit anything. Choose a JSON Schema validator/version compatible with the schema draft emitted by your selected registry.

## Error handling

| Symptom/result | Meaning or first checks |
| --- | --- |
| XML load throws `IOException` | File/resource existence and read access |
| XML load throws `SAXException`/`ParserConfigurationException` | XML syntax, parser security settings and supported document format; do not disable DOCTYPE restrictions to hide the failure |
| Definition/encode throws `IllegalArgumentException` | Unsupported metadata/type/version, missing envelope, bad discriminators or invalid wire value/length; inspect the exact message |
| Payload decoder returns `null` or encoder returns empty bytes | Unsupported selection/PGN for the chosen codec path; consult the specific API contract |
| `FrameHandler` returns empty | Ignored empty input or incomplete reassembly; retain necessary handler state |
| `UnknownMessage` | Inspect `UnknownReason`, detail, identifier and retained raw payload; distinguish invalid frame, unsupported PGN and decode failure |
| Native socket open/read/write fails | Linux support, interface state, privileges, kernel/driver support and JNA native loading |
| Serial decode returns no frame | Correct adapter codec, stream format/termination, EOF and physical port setup |
| Queue drops/rejects messages | Selected policy, queue depth, offered load, delegate failures and bandwidth configuration |
| Output names changed after XML upgrade | CANboat canonical IDs/variants; compare matched definition versions and [migration notes](canboat-migration.md) |

Retain enough raw metadata for diagnosis, especially the schema/protocol version and original frame/payload. Do not discard every unknown message as an ordinary empty read.

## Deployment checks

The examples validate offline library interactions. Physical CAN access, serial adapter configuration, bus load, address conflicts, native cancellation behaviour and FD capability require target-platform testing. Configure a virtual interface for initial I/O tests and make transmission explicit; opening the wrong real interface is an application deployment error, not something the payload codec can detect.

[Existing Raspberry Pi setup notes](../canbus-core/pi_setup.md) cover board-specific background. Treat operating-system/package commands there as deployment notes, not automatic library installation.
