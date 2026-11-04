# Offline CAN-log decoding

`CanLogToJsonDecoder` reads a classic extended-frame candump-style text file and writes JSON diagnostics/decoded messages. It does not open a CAN device or transmit anything.

Build the checkout and consumer classpath using [the examples guide](../examples/README.md), then run from the repository root:

```sh
java -cp "$CLASSPATH" io.mapsmessaging.canbus.j1939.n2k.tools.CanLogToJsonDecoder examples/config/offline-can.log /tmp/canbus-decoded.json
```

Choose a writable output path for your platform. The decoder creates/truncates that file; input and output must be different files. On Windows, use a writable path such as `canbus-decoded.json` and the documented `;` classpath separator.

## Input format

Each line has a decimal timestamp, interface name, exactly eight hexadecimal identifier characters, `#`, and hexadecimal payload bytes:

```text
(1.000000) vcan0 19F80323#0164407E058003F5
```

The sample is synthetic: a PGN 129027 payload used by the runnable payload example. The second line in [the supplied log](../examples/config/offline-can.log) is deliberately malformed to demonstrate diagnostics.

This parser does not accept every can-utils display mode. Its current regular expression expects the compact classic `ID#DATA` form, not bracketed DLC output, 3-digit standard identifiers, remote-request notation or CAN FD `##` syntax. Odd-length/invalid hexadecimal payloads become invalid-line events.

## Output contract

Despite the CLI's historical `<output-jsonl>` usage label, `decode` writes **one JSON array**, not NDJSON/JSONL. Read it using a JSON array parser. It emits per-frame/decoded diagnostic events, so output counts need not equal completed-message counts.

Events include line numbers and, where a frame parsed, timestamps, interface, identifier, PGN, addressing and raw payload details. Common `eventType` values describe completed/unknown messages, invalid lines and fast-packet tracking diagnostics. Use the actual `eventType`, `decoded`, `reason` and payload fields rather than assuming every array element is application data.

Malformed input lines do not stop the entire conversion. Payload decode failures and incomplete fast packets have explicit diagnostic events. File open/read/write errors still propagate as I/O failures; an interrupted conversion may leave an incomplete JSON array.

## Definitions and framing limits

The CLI uses the default NMEA factory through its registry-building helper. It has no XML-path or J1939-mode command-line switch. To decode using your own definitions, construct `N2kMessageParser`/`FrameHandler` explicitly and call the public `CanLogToJsonDecoder(FrameHandler)` constructor, then `decode(inputPath, outputPath)`.

The tool has a fixed PGN list for its additional fast-packet diagnostic tracker; this is separate from the registry-aware message decoder. A new XML definition is not automatically added to that diagnostic list. Do not treat the tool as a complete validator of every vendor transport or a replay engine.

The normal library APIs are described in [payloads and schemas](payloads-and-schemas.md), and custom definition loading in [dynamic definitions](dynamic-definitions.md).
