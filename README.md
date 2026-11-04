# Canbus Interface

Modular Java libraries for CAN device access, J1939 identifiers, NMEA 2000/J1939 payload definitions and CANaerospace parsing. They can be embedded without running the MapsMessaging server.

## Start here

1. Read the [integration overview](docs/overview.md) to choose the required modules.
2. Build and run the [complete examples](examples/README.md), including hardware-free demonstrations.
3. Use [dynamic definitions](docs/dynamic-definitions.md) to load your own XML at runtime and replace application registries safely.

| Module/artifact | Responsibility | Guide |
| --- | --- | --- |
| `canbus-core` | Raw CAN frames, Linux SocketCAN, stream/serial adapter framing and queued writes | [Devices and streams](docs/devices-and-streams.md) |
| `canbus-J1939` | Build/parse 29-bit J1939 identifiers, including PDU1/PDU2 rules | [Identifiers](docs/canaerospace-and-identifiers.md) |
| `canbus-nmea2000` | XML loading, compiled payload registries, JSON/schema conversion and N2K fast packets; separate NMEA/J1939 catalogs | [Payloads and schemas](docs/payloads-and-schemas.md) |
| `canbus-events` | YAML-driven CANaerospace parsing and typed values | [CANaerospace](docs/canaerospace-and-identifiers.md) |

Artifact names are case-sensitive: the identifier module is `canbus-J1939`. The parent `io.mapsmessaging:canbus` is a Maven aggregator (`pom`), not the runtime JAR to add to an application.

## Maven usage

For NMEA 2000 or definition-based J1939 payload handling:

```xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>canbus-nmea2000</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
```

This brings the core and identifier modules transitively. Add `io.mapsmessaging:canbus-events` at the same version for CANaerospace. See [setup](examples/README.md#build-against-this-checkout) for snapshot resolution and a complete consumer POM.

These guides describe the current development API (`1.1.0-SNAPSHOT`). Use documentation from the same tag as a released artifact, especially when migrating field names and XML formats. The POM targets Java 21; build and run with a compatible full JDK.

## Runtime definitions

NMEA/J1939 PGN metadata is loaded from XML, not generated into Java classes. Applications can parse compatible XML from the classpath, a file or an input stream, then compile a new registry. Updating the file does not refresh an existing registry or the static factory caches. The [reload example](examples/java/XmlReloadExample.java) shows application-owned replacement while retaining the previous configuration on failure.

CANaerospace uses YAML through a separate schema registry. Keep its schema/parser lifecycle independent of NMEA/J1939 XML.

## Scope and operational details

- Device access supplies frames; protocol modules interpret them. SocketCAN requires a configured Linux CAN interface.
- CAN FD representation/device capabilities do not imply the classic N2K framing layer supports CAN FD.
- The J1939 identifier module does not implement payload decoding, address claiming or J1939 BAM/RTS/CTS transport. Payload definitions are available separately through the NMEA module.
- The embedding application owns interface configuration, lifecycle, routing, bus addressing, concurrency and permission to transmit.

See [operations and troubleshooting](docs/operations.md) and [offline CAN-log decoding](docs/offline-tools.md).

## Build

```sh
mvn clean install -Dgpg.skip=true -Ddependency-check.skip=true
```

The command builds/installs locally; it does not publish a release. Hardware tests may need a configured `vcan0`. For source and runnable example build details, use the [examples guide](examples/README.md).

## Definitions, attribution and license

The bundled CANboat definitions and RDS character mapping derive from [CANboat](https://github.com/canboat/canboat), whose contributors developed the public PGN definitions through reverse engineering. Preserve the embedded upstream notices when redistributing definition files. See [CANboat migration and provenance](docs/canboat-migration.md) for the pinned source/version and compatibility history.

This repository is licensed under Apache License 2.0 with the Commons Clause; see [LICENSE](LICENSE). Upstream definition provenance is distinct from the library license. [Release notes](RELEASE_NOTES.md) describe release changes.

[DeepWiki](https://deepwiki.com/Maps-Messaging/canbus_interface)
