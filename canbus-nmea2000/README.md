# CAN Bus Interface

Java CAN bus integration libraries for MapsMessaging.

This repository provides the lower-level CAN bus building blocks used by the
MapsMessaging server and related tooling. It separates raw CAN device access
from higher-level protocol decoding so each layer stays honest about what it
does.

The repository is organised as a set of Maven subprojects:

- **CAN bus device layer**: raw CAN frame access over SocketCAN, virtual CAN,
  serial CAN adapters, and Waveshare USB-CAN style devices
- **CAN bus events**: event-level protocol decoding, currently including
  CANaerospace
- **J1939**: SAE J1939 support
- **NMEA 2000**: N2K payload decoding based on public CANboat metadata

---

## Project Scope

This repository is not a single monolithic CAN stack.

It is intentionally split into layers:

1. Open or connect to a CAN-capable device
2. Read and write raw CAN frames
3. Decode frames into protocol-specific events where supported
4. Let higher layers handle routing, transformation, storage, and messaging

That separation matters because CAN itself is only a broadcast frame bus.
Everything above that belongs in a protocol-specific module, not in the raw
device layer.

---

## Subprojects

### CAN Bus Device Layer

The device layer provides raw CAN frame access.

It supports:

- Linux SocketCAN interfaces such as `can0` and `can1`
- Linux virtual CAN interfaces such as `vcan0`
- Serial CAN adapters
- Waveshare USB-CAN style devices
- Classic CAN frames
- CAN FD capability detection where supported by the device and interface

This layer is deliberately thin. It opens the device, reads frames, writes
frames, and exposes capabilities. It does not decode protocols, fragment
messages, reassemble multi-frame payloads, or invent transport semantics.

Higher layers are expected to decide what a frame means.

---

### CAN Bus Events

The event layer converts raw CAN frames into structured protocol events.

Currently supported:

- **CANaerospace**

This module is intended for protocols where the CAN identifier and payload can
be interpreted into meaningful event data for routing, inspection, logging, or
conversion into other message formats.

The event layer sits above the raw CAN device layer and below the wider
MapsMessaging event pipeline.

---

### J1939

The J1939 module provides support for SAE J1939-style CAN traffic.

J1939 is a higher-level CAN protocol commonly used in heavy vehicles,
industrial systems, marine engines, and machinery. It defines message structure,
parameter groups, source addressing, and related semantics above raw CAN frames.

This module belongs above the raw CAN device layer. It should not be treated as
a device driver.

---

### NMEA 2000

The NMEA 2000 module provides payload decoding for N2K messages.

It parses PGN definitions derived from public CANboat XML metadata, compiles
message definitions, and decodes N2K payload bytes into structured Java values.

The N2K decoder is intentionally scoped to payload decoding. CAN transport,
frame acquisition, fast-packet handling, source addresses, and device-level
behaviour are handled outside the payload codec.

Important limitations:

- Only public PGNs from CANboat-style metadata are included
- Proprietary PGNs are not decoded unless definitions are supplied elsewhere
- Some metadata contains inconsistencies or ambiguities
- Some PGNs may decode partially or require special handling

The decoder is designed for inspection, telemetry ingestion, validation, and
protocol bridge work.

---

## Layering Model

```text
+--------------------------------------------------+
| MapsMessaging server / routing / transformation  |
+--------------------------------------------------+
| Protocol event modules                           |
| - CANaerospace                                   |
| - J1939                                          |
| - NMEA 2000                                      |
+--------------------------------------------------+
| Raw CAN device layer                             |
| - SocketCAN                                      |
| - vcan                                           |
| - serial CAN adapters                            |
| - Waveshare USB-CAN                              |
+--------------------------------------------------+
| Linux kernel / serial devices / CAN hardware     |
+--------------------------------------------------+
```

The raw device layer should remain protocol-agnostic. Protocol modules should
consume frames and produce meaningful decoded events or payload structures.

---

## Design Goals

- Keep raw CAN access separate from protocol decoding
- Avoid hiding kernel or device behaviour
- Support both physical and virtual CAN interfaces
- Allow protocol modules to evolve independently
- Provide deterministic decoding where metadata and protocol rules allow it
- Fail explicitly rather than silently corrupting decoded data

---

## What This Repository Does Not Do

This repository does not try to make CAN look like TCP, UDP, MQTT, or any other
message transport invented by people who wanted life to be tidier than physics
allows.

It does not provide:

- A universal CAN protocol abstraction
- Guaranteed decoding of every proprietary payload
- Automatic interpretation of unknown frames
- Safety-certified marine, automotive, or aerospace behaviour
- A single all-knowing transport stack

Each module has a defined responsibility. That boundary is deliberate.

---

## Intended Use

These libraries are intended for:

- MapsMessaging CAN bus integration
- CAN telemetry ingestion
- Protocol bridge development
- Log replay and inspection tools
- Device and protocol validation
- Embedded and industrial data collection

A typical pipeline is:

1. Read CAN frames from a device
2. Pass frames into the relevant protocol module
3. Decode supported frames into structured events or payload data
4. Publish, route, transform, store, or bridge the decoded output

---

## Platform Notes

SocketCAN and `vcan` support require Linux.

Serial CAN adapters can be used where supported by the relevant device module,
but behaviour depends on the adapter firmware, framing format, and configured
baud rate. Naturally, because one standard bus was apparently not enough.

---

## Build

This is a Maven multi-module project.

From the repository root:

```bash
mvn clean install
```

Individual modules may also be built separately from their subproject
directories.

---

## License

Apache License 2.0 with the Commons Clause.

See the repository license files for details.
