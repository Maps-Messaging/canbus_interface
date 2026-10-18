# CANbus 1.0.1

Version 1.0.1 improves reliability in CAN device access, NMEA 2000 decoding and CANaerospace handling. It also strengthens XML parsing and expands test coverage of malformed frames, edge cases and device failures.

## Fixes and improvements

### CAN device interfaces
- Close SocketCAN resources when network interface discovery fails, preventing leaked sockets during unsuccessful device initialisation.
- Improve serial CAN device shutdown and error handling, including propagation of close failures.
- Make SocketCAN interface lookup more reliable when the `ip` utility is installed in standard system directories.

### NMEA 2000
- Harden XML dialect loading against external entity declarations.
- Improve thread safety when initialising the NMEA 2000 schema cache.
- Correct candump-to-JSON output for decoded arrays and compare frame payload bytes by content.
- Improve decoding behaviour and recovery when PGNs or fields are not recognised.

### CANaerospace
- Make unknown data-type fallback handling explicit, preserving raw-frame processing when a defined type is unavailable.
- Extend validation around binary codec formats, identifier resolution and schema lookup.

## Compatibility

- This is a maintenance release; no intentional public API-breaking changes are included.
- The library continues to target Java 21.
- The Maven reactor includes `canbus-core`, `canbus-events`, `canbus-J1939` and `canbus-nmea2000`. All modules share version `1.0.1`.
- NMEA 2000 XML dialect files containing external entities are now rejected. Applications should provide self-contained, trusted schema definitions.

## Maven modules

Use the module relevant to your integration, for example:

~~~xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>canbus-nmea2000</artifactId>
  <version>1.0.1</version>
</dependency>
~~~

The other published module artifact IDs are `canbus-core`, `canbus-events` and `canbus-J1939`.

## Test coverage

This release adds or expands regression tests for SocketCAN and virtual CAN interfaces, serial-device failures, CANaerospace codec boundaries and schema resolution, and NMEA 2000 parsing, string encoding and decoder recovery.
