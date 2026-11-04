# Application-managed XML definitions

Applications can parse CANboat/legacy N2K XML from classpath resources, files or streams, compile a new registry and construct matching payload/schema services. This is an explicit workflow: the library does not watch files, reload factories, merge custom definitions into built-ins or implement your deployment/version policy.

[XmlReloadExample.java](../examples/java/XmlReloadExample.java) runs offline against authored [custom-canboat.xml](../examples/config/custom-canboat.xml). It loads a file, prepares a stream replacement, publishes an application-owned `AtomicReference` bundle, retains old readers and rejects an incompatible version. Use the shared [build/run instructions](../examples/README.md).

## Inputs and stream ownership

| API | Input | Lifecycle |
| --- | --- | --- |
| `N2kXmlDialectParser.parseFromClasspath()` | Packaged `n2k/canboat.xml` | Opens/closes internally |
| `parseFromClasspath(String)` | Context-classloader path; strips one leading `/` | Opens/closes internally; missing resource throws `IllegalArgumentException` |
| `parseFromFile(Path)` | Filesystem path; relative paths use process working directory | Opens/closes internally |
| `parse(InputStream)` | Application-supplied XML bytes | Use caller-owned try-with-resources rather than assuming an application stream-close policy |

Parsing declares `IOException`, `SAXException` and `ParserConfigurationException`; metadata/version/numeric errors can also throw unchecked exceptions. Handle failure before replacing active definitions. Classpath loading falls back to the parser's own class loader when the context loader is null.

Source: [N2kXmlDialectParser](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/parser/N2kXmlDialectParser.java).

## Dialect/version selection

| XML characteristic | Behavior |
| --- | --- |
| Root has direct `SchemaVersion` child | CANboat database/version/protocol parsing and canonical metadata |
| No direct `SchemaVersion` child | Legacy model/processor path |
| CANboat `SchemaVersion` starts with `2.` | Accepted version family |
| Other CANboat `SchemaVersion` | Rejected |
| Protocol `nmea2000` or `j1939` | Accepted |
| Missing canonical protocol | Defaults to `nmea2000` |
| Other canonical protocol | Rejected |

`Version` is database metadata, defaulting to `unknown`. Accepting `2.*` does not establish support for every future field/feature. Unknown canonical field types are rejected; some conditions/references fail only at encode/decode time. Parse/compile success is insufficient runtime validation.

Legacy `n2k/NMEA_database_1_300.xml` remains explicitly loadable. Legacy parsing normalizes an initial uppercase field-ID character and uses a smaller processor set, with number/repeat heuristics for some metadata. Sharing a list API does not give legacy input canonical variant/lookup semantics.

Sources: [CanboatDatabase](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatDatabase.java), [N2kXmlDialectParser](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/parser/N2kXmlDialectParser.java), [CanboatPayloadCodec](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatPayloadCodec.java).

## Preserve canonical metadata when compiling

For CANboat input, the declared `List<N2kMessageDefinition>` result is actually `CanboatDefinitions`, which carries its `CanboatDatabase`. The compiler recognizes that concrete object. Pass the original parser result directly:

```java
List<N2kMessageDefinition> definitions = N2kXmlDialectParser.parseFromFile(path);
N2kCompiledRegistry registry = N2kCompiler.compile(definitions);
N2kMessageParser parser = new N2kMessageParser(registry);
N2kSchemaRegistry schemas = new N2kSchemaRegistry(registry);
```

Copying into an ordinary `ArrayList`, mapping through a stream or concatenating ordinary lists loses the database metadata. The compiler then chooses the legacy path, changing codec/schema behavior and losing conditional variant/lookup semantics. Do not merge canonical databases by concatenating Java lists. Produce/review one coherent XML database, parse it once and compile that result.

Legacy duplicate PGNs overwrite the main registry entry with the later compiled definition. Canonical definitions retain the first representative while separate metadata selects variants. Ordering is not a documented custom-override deployment mechanism.

Sources: [CanboatDefinitions](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/canboat/CanboatDefinitions.java), [N2kCompiler](../canbus-nmea2000/src/main/java/io/mapsmessaging/canbus/j1939/n2k/compile/N2kCompiler.java).

## Demonstration metadata

The fixture defines one PGN only; it does not include built-in definitions/fallback ranges:

| Metadata | Demo value |
| --- | --- |
| Schema / database version | `2.8.0` / `demo-v1` |
| Protocol | `nmea2000` |
| PGN / variant ID | `130816` / `demoMeasurement` |
| Transport / size | `Single` / two bytes |
| Field | Unsigned 16-bit `NUMBER`, ID `value`, bit offset zero |
| Resolution / offset | `0.1` / zero |
| Hex `d204` | Little-endian raw `1234`, physical `123.4` |

This authored demonstration is not a manufacturer-assigned wire layout. Real proprietary definitions need their actual manufacturer/industry/discriminator fields and device contract.

The example replaces scale `0.1` with `0.01` in an in-memory XML copy. The same bytes then mean `12.34`. This deliberate interpretation change illustrates why definition versions belong with reproducible telemetry/replay metadata; it does not modify the fixture file.

## Prepare, verify, publish

Keep registry, parser and schema service in one application generation:

1. Parse candidate XML outside the active reader path.
2. Compile the original returned list, retaining CANboat metadata.
3. Check required protocol, exact PGN and expected variant.
4. Construct payload parser and schema registry from the same compiled registry.
5. Generate needed schemas and exercise independent decode/encode vectors with expected physical values.
6. Publish the fully prepared bundle only after checks pass.
7. Readers obtain one bundle once per operation and use it consistently.

The example holds these objects in a private record and publishes with `active.set(candidate)`. Its unsupported `SchemaVersion` `9.0.0` candidate fails before publication; the previous accepted bundle remains active. A retained first-generation reader still sees `123.4` after new readers see `12.34`.

Atomic publication does not make every contained object immutable/thread-safe. Treat definitions as read-only and copy mutable Gson schemas before adapting them. Serialize concurrent reload writers or use an explicit revision/CAS policy: a slower older candidate must not overwrite a newer generation. The example has one writer and no revision arbitration.

## Transport state during reload

`FrameHandler` and `FramePacker` retain their construction-time parser. Publishing another parser does not retarget existing framing objects; a handler also owns incomplete fast-packet state.

Switch definitions at a controlled receive boundary. Either finish old in-flight messages under their old generation or explicitly discard those assemblies/create a new handler. Do not send half a packet to one generation and its continuations to another. See [framing limits](payloads-and-schemas.md#transport-and-size-limits).

When replacing packers, decide whether to preserve a shared `SequenceProvider` for the same outbound scope. A new default packer starts an independent counter. The XML example operates on complete payloads, so it does not claim that atomic parser publication safely swaps live transport sessions.

## Validation and trust limits

The XML parser enables secure processing, disallows DOCTYPE, disables external general/parameter entities and DTD/schema access, and disables XInclude/entity expansion. The [security tests](../canbus-nmea2000/src/test/java/io/mapsmessaging/canbus/j1939/n2k/parser/N2kXmlDialectParserSecurityTest.java) exercise these boundaries. Parsing is namespace-unaware and uses tag-name matching.

These settings do not authenticate a file, perform XSD validation, or define your maximum file/PGN/field count and memory budget. Apply source integrity/resource limits and compatibility policy before publication.

| Candidate problem | Application response |
| --- | --- |
| File/parse/setup failure | Report failure; retain accepted generation |
| Wrong protocol or PGN/variant coverage | Reject before publication |
| Unsupported condition/reference, bad scale or ambiguous layout | Exercise required vectors; reject unexpected behavior |
| Changed units/scale/offset/transport | Require an explicit compatibility/version decision |
| Missing typed coverage | Preserve observable raw fallback and distinguish it from typed telemetry |
| Schema generation succeeds | Still verify wire/JSON behavior; generation is not conformance proof |

Factory caches remain unchanged by this workflow. Use application-owned references for runtime updates and record protocol/database/version with the processing generation. Continue with [payload/schema contracts](payloads-and-schemas.md) and [fixture provenance](../examples/config/README.md).
