# Example configuration and input data

[custom-canboat.xml](custom-canboat.xml) is authored for this repository's examples. It is not imported CANboat vendor metadata and is not a vendor-approved proprietary protocol. Do not transmit it on a live network as an assigned device message.

It uses the supported CANboat schema version `2.8.0`, protocol `nmea2000`, PGN `130816`, a `Single` two-byte payload and one unsigned little-endian 16-bit field named `value`. The physical value is `raw × 0.1`; hex payload `d204` therefore decodes as `123.4`.

[XmlReloadExample.java](../java/XmlReloadExample.java) first loads this file with `parseFromFile`, then changes its in-memory resolution to `0.01` and parses a caller-owned `InputStream`. The same payload then means `12.34`. It prepares a new registry/parser/schema bundle before atomically replacing its own application reference, retains the old reader's interpretation and rejects an unsupported schema-version replacement.

The example does not edit this file, watch directories, merge it into built-in metadata or reset a factory singleton. This deliberate scale change illustrates why applications must govern definition versions. File authenticity, review, size limits, compatibility policy and concurrent reload ordering belong to the embedding application.

Use the shared [build/run instructions](../README.md) and [dynamic definitions guide](../../docs/dynamic-definitions.md).

## Synthetic CAN log

[offline-can.log](offline-can.log) contains a synthetic PGN 129027 classic extended frame and one deliberately malformed line. It is authored example data, not a capture from a device. The [offline decoder guide](../../docs/offline-tools.md) shows how to produce a JSON array with one decoded-message event and one invalid-line event. The tool does not transmit the contents.
