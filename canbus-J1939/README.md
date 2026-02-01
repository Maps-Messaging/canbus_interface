# J1939 CAN Identifier Utilities

This module provides a **minimal, dependency-light implementation** for building and parsing **29-bit extended CAN identifiers** using **J1939 / NMEA 2000 framing rules**.

It is intentionally small and focused: no transport, no socket handling, no payload logic. Just CAN identifiers done correctly.

## Scope

This project covers **only**:

- Building a 29-bit CAN identifier from J1939 fields
- Parsing a 29-bit CAN identifier back into its logical components
- Correct handling of **PDU1 vs PDU2** rules
- Compatibility with **J1939** and **NMEA 2000** identifier layouts

If you are looking for CAN drivers, SocketCAN bindings, fast-packet handling, or protocol decoding, this is not that project.

## Package

```
io.mapsmessaging.canbus.j1939
```

## Provided Classes

### `CanIdBuilder`

Stateless utility for constructing a 29-bit CAN identifier.

Inputs:
- PGN
- Priority
- Source Address
- Destination Address (used only for PDU1)

Behavior:
- Automatically applies J1939 rules:
    - **PF < 240** → PDU1 → destination encoded in PS
    - **PF ≥ 240** → PDU2 → destination implied global (255)

### `CanId`

Immutable value object representing a parsed CAN identifier.

Exposes:
- Priority
- PGN
- Source Address
- Destination Address

Parsing logic:
- Extracts PF, PS, DP correctly
- Reconstructs PGN according to PDU rules
- Assigns destination correctly (explicit or global)

## Design Notes

- Uses `int` internally, masked to **29 bits**
- No allocations during build
- No Lombok outside of trivial getters
- No external dependencies

This module is designed to be embedded anywhere J1939 identifiers are needed: servers, gateways, simulators, or test harnesses.

## Relationship to Other Work

This library is intended as a **foundational building block** for higher-level CAN, J1939, NMEA 2000, and CANopen integrations within the MapsMessaging ecosystem.

Future modules may layer on:

- CAN device abstractions
- Protocol match / dispatch logic
- NMEA 2000 fast-packet reassembly
- J1939 transport protocols
- CANopen object dictionary support

Those concerns are deliberately kept out of this project.

## License

Apache License 2.0 with Commons Clause.

