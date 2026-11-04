# CANaerospace frames and J1939 identifiers

`canbus-events` decodes the fixed CANaerospace payload layout using YAML definitions. `canbus-J1939` builds and parses
J1939/NMEA 2000 identifiers. These are independent APIs: CANaerospace identifiers are standard 11-bit values, whereas
the J1939 utilities use 29-bit extended identifiers. Neither module opens a device or publishes messages.

See [examples/README.md](../examples/README.md) for compilation and runtime classpaths. Both
[CanaerospaceExample.java](../examples/java/CanaerospaceExample.java) and
[J1939IdentifierExample.java](../examples/java/J1939IdentifierExample.java) run without CAN hardware.

## CANaerospace startup and YAML definitions

Load the bundled `canaerospace_schema.yaml`, an explicitly named classpath resource, or a file:

```java
CanaerospaceSchemaRegistry bundled = CanaerospaceSchemaRegistry.loadFromClasspath();
CanaerospaceSchemaRegistry named = CanaerospaceSchemaRegistry.loadFromClasspath("my-canaerospace.yaml");
CanaerospaceSchemaRegistry custom = CanaerospaceSchemaRegistry.load(Path.of("config/canaerospace.yaml"));
CanaerospaceFrameParser parser = new CanaerospaceFrameParser(custom);
```

The registry maps IDs to identifier metadata, message-type ID ranges to names, and payload type numbers to type names.
It expands `identifierRanges` at construction; explicit identifiers take precedence over expanded entries. Range bounds
must be within 0..2047. Range titles substitute `{n}` with a one-based index. `findIdentifier`, `findMessageType` and
`findDataTypeNameByNumber` return `Optional` values. `getSchema()` exposes the loaded YAML model, not a JSON Schema.

A complete small custom YAML file suitable for the example is:

```yaml
meta:
  schema: CANaerospace
  version: 'example-1'
messageTypes:
  - name: NOD
    idRange:
      min: 300
      max: 1799
dataTypes:
  byNumber:
    6: SHORT
identifiers:
  - id: 300
    group: Flight State / Air Data
    title: Body longitudinal acceleration
    name: bodyLongitudinalAcceleration
    messageType: NOD
    dataType: SHORT
    units: g
    resolution: 0.000488
    range:
      min: -16
      max: 16
```

The loader binds YAML to the existing Java schema classes using SnakeYAML. Use trusted deployment configuration and
actual property names from the bundled file. Adding identifier metadata does not add a new binary codec: `dataType`
names must match `DataTypeCodec`'s implemented names, or the value remains raw bytes. `dataTypes.byNumber` supplies the
payload type-name lookup; `byName` metadata alone does not populate that lookup.

At startup, load and check the chosen registry, then construct its parser. For an application-controlled update, load a
new registry from the replacement file, construct a new parser, validate representative frames and explicitly replace
the parser reference in the host application. Keep failures on the existing parser if that is the desired host policy.
There is no built-in watcher, schema timer or automatic reload. Do not mutate a registry's exposed schema model and
expect its derived lookup maps to rebuild. Coordinate replacement and in-flight messages in the host.

## Decode, encode and serialize one frame

The parser requires exactly eight payload bytes:

| Byte(s) | Interpretation |
| --- | --- |
| 0 | Unsigned node ID. |
| 1 | Unsigned payload data-type number. |
| 2 | Unsigned service code. |
| 3 | Unsigned message code. |
| 4..7 | Four-byte data field interpreted by the identifier's schema `dataType`. |

`CanaerospaceExample` encodes raw value `1000` using `DataTypeCodec.encode("SHORT", 1000)`, builds the four header
bytes, parses ID `300`, and verifies the data bytes encode back unchanged. Its optional argument selects the YAML file;
without an argument it loads the bundled resource. A custom schema must retain ID300 as `SHORT` and type number 6 as
`SHORT`, or this deliberately fixed example rejects it.

With the bundled schema, the output starts with:

```text
Payload hex: 2A060001000003E8
Identifier metadata (not JSON Schema):
```

The following JSON contains identifier `300`, name `bodyLongitudinalAcceleration`, units `g`, resolution `0.000488` and
range -16..16. The decoded JSON contains node `42`, type number `6`, payload/schema type `SHORT`, service `0`, message
`1`, raw value `1000`, engineering value approximately `0.488` and `dataTypeMismatch: false`. Message type is `NOD`.
Floating-point output can include normal binary rounding. The units remain those declared by the schema; this example
retains `g` from the bundled definition rather than silently converting to metres per second squared.

Gson serialization in the example is ordinary DTO/metadata JSON. There is no CANaerospace JSON Schema generator or
JSON-to-complete-frame encoder in this module. Gson omits null properties by default. To transmit, the host supplies
the node, payload type, service/message codes and standard CAN identifier, then places the four codec bytes at offset
4 in an eight-byte frame. `DataTypeCodec.encode` accepts raw values; it does not divide engineering values by schema
resolution or apply metadata ranges. The host owns unit conversion, rounding, range checks and transmission policy.

## Codec and parser limits

Multi-byte codec values are big-endian. Every codec input/output field is four bytes, regardless of scalar width:

| Type(s) | Current representation |
| --- | --- |
| `FLOAT`, `LONG`, `ULONG` | Four-byte float, signed 32-bit integer, unsigned 32-bit integer. |
| `SHORT`, `USHORT` | Signed/unsigned 16-bit value at bytes 2..3. |
| `BSHORT` | Unsigned 16-bit value at bytes 0..1. |
| `CHAR`, `UCHAR`, `ACHAR` | Signed/unsigned byte at offset 0 (`ACHAR` is numeric). |
| `CHAR2`, `UCHAR2`, `ACHAR2` | Two numeric byte elements at offsets 0..1. |
| `CHAR4`, `UCHAR4` | Four numeric byte elements. |
| `SHORT2`, `USHORT2` | Two big-endian 16-bit elements. |
| `BLONG`, `MEMID`, `CHKSUM` | Unsigned 32-bit bit pattern. |
| `ACHAR4` | Four bytes decoded as a string; encoding uses ISO-8859-1, truncates/pads to four bytes. |
| `VARIABLE3`, `UVARIABLE3` | Signed/unsigned 24-bit value at offsets 1..3. |
| `NODATA` | Decodes to null; encoding yields four zero bytes. |
| Other type names | Decode retains four raw bytes; encoding requires a four-byte `byte[]`. |

The YAML lists additional types such as `ERROR`, `BCHAR`, `BCHAR2`, `BCHAR4` and `BSHORT2`; those names currently take
the raw-byte fallback. A named type in YAML does not imply a specialized codec exists. There is no eight-byte `DOUBLE`
codec in this four-byte data field. `ACHAR4` decode currently uses the platform default charset; arbitrary non-ASCII
text is not guaranteed to round-trip with its ISO-8859-1 encoder. Numeric encoders accept Java `Number` conversions;
the host must validate precision and fractional values before passing them when truncation is unacceptable.

The parser chooses the decoder from identifier metadata, rather than the payload type byte. It marks
`dataTypeMismatch` only when both type names are known and unequal; an unknown payload type number does not itself set
the flag. A mismatch does not throw. Unknown identifiers still yield header fields/data bytes with absent identifier
metadata and no typed value. Resolution multiplies numeric raw values; an unscaled float is returned as its engineering
value, while an unscaled integer has a null engineering value. Declared ranges are exposed, not enforced.

The fixed layout and codec byte order are implemented in Java; editing `frame.payloadLayout`, `frame.dlc` or
`frame.endianness` in YAML does not reconfigure them. The parser does not accept an extended-frame flag and does not
validate all raw frame metadata. The host should dispatch only the intended standard CAN data frames and reject error,
remote-request or otherwise unsuitable frames before calling it.

`nativeFormatChannels`, node-service descriptions, baseline node IDs and time-slot allocations are schema metadata.
The parser does not implement native-channel framing, CPDLC/ADS-B decoding, node-service exchanges or timed scheduling.
Do not feed an arbitrary native-format payload into the fixed header decoder and assume it is supported. The bundled
time-slot examples are illustrative; their type annotations are not authoritative parser definitions. In particular,
ID300 is `SHORT` in the identifier table used by the example.

## J1939/NMEA 2000 identifier build and decode

`CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress)` returns the numeric 29-bit identifier.
`CanId.parse(identifier)` extracts immutable field values and supplies PDU/broadcast helpers. This module does not load
XML, interpret SPNs or reassemble transport packets. The bundled `canboat-j1939.xml` resource does not change that API.
Definition-driven J1939 payload parsing belongs to [canbus-nmea2000](../canbus-nmea2000/README.md), using that module's
XML loader, compiler, parser and framing APIs.

| Rule | PDU1 (`PF < 240`) | PDU2 (`PF >= 240`) |
| --- | --- | --- |
| PGN low byte | Must be zero. | Contains the group extension. |
| Wire PS byte | Destination address. | PGN low byte. |
| Parsed destination | Wire destination, including global address 255. | Always 255. |
| `getDestinationAddressOrNull()` | Destination value. | null. |
| `getGroupExtensionOrNull()` | null. | Group extension value. |
| `isBroadcast()` | True when destination is 255. | Always true. |

The builder validates priority 0..7, source and destination 0..255, and PGN 0..`0x1FFFF` (one data-page bit). Destination
is validated even for PDU2, where its value is not encoded. A PDU1 PGN with a nonzero low byte is rejected. This API
does not encode the additional extended-data-page bit as part of a wider PGN. `parse` masks its input to 29 bits; it is
not a validator for SocketCAN flags or arbitrary native frame words. Supply the cleaned identifier and separately set
`extendedFrame=true` when creating a raw `CanFrame` in `canbus-core`. Error/RTR/native adapter flags are separate concerns.

`J1939IdentifierExample` builds both layouts and checks the round trip. Expected output:

```text
PDU1 identifier=0x18EA2380 priority=6 pgn=0x0EA00 source=128 destination=35
  pdu1=true pdu2=false broadcast=false destinationOrNull=35 groupExtensionOrNull=null
PDU2 identifier=0x09F80142 priority=2 pgn=0x1F801 source=66 destination=255
  pdu1=false pdu2=true broadcast=true destinationOrNull=null groupExtensionOrNull=1
PDU1 destination 255 is broadcast: true
```

These are identifier demonstrations, not generated J1939 request or NMEA 2000 payloads. The host must supply the correct
payload, address-claim behavior, transport/reassembly requirements and bus timing for the deployed protocol.
