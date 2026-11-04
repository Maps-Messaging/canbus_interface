# Complete Canbus examples

These examples use the current `1.1.0-SNAPSHOT` source API. They are standalone applications outside the production `src/main/java` tree. No MapsMessaging server, broker or dependency-injection framework is required.

## Build against this checkout

Use a full JDK 21 or newer supported by the selected dependencies, plus Maven. Run from the repository root:

```sh
mvn -DskipTests -Dgpg.skip=true -Dmaven.javadoc.skip=true -Ddependency-check.skip=true install
mvn -f examples/pom.xml package dependency:copy-dependencies -DincludeScope=runtime
```

The first command builds and installs the reactor artifacts locally; it skips test execution, Javadoc, signing and dependency scanning to prepare the examples. It does not deploy artifacts. Use the repository's normal build if you also want its test/verification lifecycle.

The consumer POM depends on `canbus-nmea2000` and `canbus-events`; core and the case-sensitive `canbus-J1939` artifact are transitive. Local installation ensures the examples use this checkout rather than another snapshot. The POM also declares the snapshot repository for dependency resolution. For a released library, select the matching module versions and tagged examples/documentation.

## Run the hardware-free examples

On Linux/macOS:

```sh
export CLASSPATH='examples/target/classes:examples/target/dependency/*'
java -cp "$CLASSPATH" J1939IdentifierExample
java -cp "$CLASSPATH" CanaerospaceExample
java -cp "$CLASSPATH" NmeaPayloadExample
java -cp "$CLASSPATH" NmeaFramingExample
java -cp "$CLASSPATH" XmlReloadExample
java -cp "$CLASSPATH" StreamCodecExample
```

On Windows PowerShell:

```powershell
$env:CLASSPATH = 'examples/target/classes;examples/target/dependency/*'
java -cp $env:CLASSPATH J1939IdentifierExample
java -cp $env:CLASSPATH CanaerospaceExample
java -cp $env:CLASSPATH NmeaPayloadExample
java -cp $env:CLASSPATH NmeaFramingExample
java -cp $env:CLASSPATH XmlReloadExample
java -cp $env:CLASSPATH StreamCodecExample
```

The Windows commands illustrate the classpath separator; execution on Windows needs its own environment validation. None of these six examples opens a device or transmits frames.

| Example | What it checks | Input/requirements |
| --- | --- | --- |
| [J1939IdentifierExample.java](java/J1939IdentifierExample.java) | PDU1/PDU2 identifiers and parsed addressing | None |
| [CanaerospaceExample.java](java/CanaerospaceExample.java) | YAML loading, fixed CANaerospace frame decode and typed-value encode | Bundled schema; optional custom YAML path retaining the demo ID/type |
| [NmeaPayloadExample.java](java/NmeaPayloadExample.java) | NMEA/J1939 complete-payload decode/encode and schema access | Bundled XML resources |
| [NmeaFramingExample.java](java/NmeaFramingExample.java) | Encode, fast-packet packing and reassembly into a known message | Bundled XML resources |
| [XmlReloadExample.java](java/XmlReloadExample.java) | Custom file/stream XML, candidate validation, explicit replacement and rejected-reload retention | [custom-canboat.xml](config/custom-canboat.xml), relative to repository root by default |
| [StreamCodecExample.java](java/StreamCodecExample.java) | In-memory standard/extended frame round trips through SLCAN and Waveshare codecs | Byte-array streams only |
| [SocketCanExample.java](java/SocketCanExample.java) | Native frame receive, or explicit virtual-interface transmit | Linux, JNA and a configured CAN/vcan interface |

Every example contains assertions that throw on an unexpected result. Printed data illustrates the API; it is not a hardware/protocol certification result.

The reload example intentionally requires the supplied demo field and metadata. Its optional XML path lets you relocate that fixture, not run the same hard-coded assertions against an arbitrary production catalog. For a real catalog, adapt the required-PGN/vector checks before publishing it.

## Optional native I/O

Configure a virtual interface using [the device guide](../docs/devices-and-streams.md#linux-socketcan). Native access may require deployment permissions; no setup command is executed automatically by the Java example.

```sh
# Observe virtual traffic in another terminal if can-utils is installed:
candump vcan0

# Send exactly one demonstration classic frame; the example restricts transmission to vcan0:
java -cp "$CLASSPATH" SocketCanExample vcan0 --send-vcan
```

Without `--send-vcan`, `SocketCanExample [interface]` waits for one incoming frame using a blocking read. It has no receive timeout and should be exercised with a deliberately arranged sender. Never substitute a real bus interface in a demonstration command without assessing the operational context.

The stream example validates codec byte framing only. Opening/configuring a serial port and initializing the real adapter remain application responsibilities.

## Configuration and offline tooling

[Configuration fixtures](config/README.md) describe the synthetic XML and CAN-log inputs. The [dynamic definitions guide](../docs/dynamic-definitions.md) explains supported file/classpath/input-stream loading and safe application replacement. [CANaerospace](../docs/canaerospace-and-identifiers.md) includes a minimal custom YAML example.

Decode the synthetic CAN log without any hardware:

```sh
java -cp "$CLASSPATH" io.mapsmessaging.canbus.j1939.n2k.tools.CanLogToJsonDecoder examples/config/offline-can.log /tmp/canbus-decoded.json
```

Choose a writable output path on your platform. The result is a JSON array with one decoded-message event and one deliberately invalid-line event. See [offline tools](../docs/offline-tools.md) for accepted input syntax and output semantics.

## Verification boundaries

Compilation and offline round trips do not exercise native CAN read cancellation, serial hardware, FD capabilities, real bus timing or application delivery guarantees. Use [operations and troubleshooting](../docs/operations.md) and target-device integration tests for those paths.

Keep bundled XML/YAML and upstream notices with the runtime artifacts. The demonstration XML/log are synthetic; they do not replace an authoritative catalog or capture corpus.
