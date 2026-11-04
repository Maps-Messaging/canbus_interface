# J1939 / NMEA 2000 identifier utilities

`canbus-J1939` builds and parses numeric 29-bit CAN identifiers. Its public classes are
`io.mapsmessaging.canbus.j1939.CanIdBuilder` and `CanId`. It provides identifier fields and PDU helpers, without device
access, address claiming, XML loading, payload decoding or transport reassembly.

Use Maven artifact `io.mapsmessaging:canbus-J1939` with the selected repository version. The identifier utilities have
no runtime library dependency; Lombok generates their getters/constructor at build time. The repository targets Java 21.

```java
import io.mapsmessaging.canbus.j1939.CanId;
import io.mapsmessaging.canbus.j1939.CanIdBuilder;

int identifier = CanIdBuilder.build(0x00EA00, 6, 0x80, 0x23);
CanId decoded = CanId.parse(identifier);
// identifier = 0x18EA2380; PGN = 0x00EA00; source = 128; destination = 35.
```

For PDU1 (`PF < 240`), the PGN low byte must be zero and the wire PS byte is the destination. For PDU2 (`PF >= 240`),
PS comes from the PGN low byte and destination is global (255). For example,
`CanIdBuilder.build(0x01F801, 2, 0x42, 0xFF)` yields `0x09F80142`.

| API | Meaning |
| --- | --- |
| `getPriority()`, `getPgn()`, `getSourceAddress()` | Parsed identifier fields. |
| `getDestinationAddress()` | Explicit PDU1 destination, or 255 for PDU2. |
| `getPduFormat()` | PF byte. |
| `getGroupExtension()` | PDU2 extension, or -1 for PDU1. |
| `isPdu1()`, `isPdu2()` | Select the identifier layout. |
| `isBroadcast()` | PDU2, or PDU1 addressed to 255. |
| `getDestinationAddressOrNull()` | Destination for PDU1; null for PDU2. |
| `getGroupExtensionOrNull()` | Extension for PDU2; null for PDU1. |

The builder validates priority 0..7, addresses 0..255, PGN 0..`0x1FFFF`, and the PDU1 low-byte rule. It validates the
PDU2 destination argument even though that value does not affect the encoded identifier. The supported layout uses one
data-page bit. `CanId.parse` masks to 29 bits instead of rejecting arbitrary input. Pass the clean identifier; keep
SocketCAN/native error, RTR and extended-frame flags separate. Raw device `CanFrame` instances carrying these identifiers
must use `extendedFrame=true`.

Run [J1939IdentifierExample](../examples/java/J1939IdentifierExample.java) with the
[example instructions](../examples/README.md). The [integration guide](../docs/canaerospace-and-identifiers.md) includes
both layouts' complete expected output and their relationship to standard CANaerospace IDs.

For definition-driven J1939 or NMEA 2000 payload parsing, use
[canbus-nmea2000](../canbus-nmea2000/README.md). Although this module packages `canboat-j1939.xml`, its identifier
classes do not load or decode that file. Payload and framing support must be selected through the other module's
implemented APIs rather than inferred from a resource filename.

Licensed under Apache License 2.0 with the Commons Clause; see the repository [LICENSE](../LICENSE).
