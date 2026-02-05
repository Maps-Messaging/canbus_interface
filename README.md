# Canbus Interface

This repository provides a modular Java implementation for working with **CAN bus–based protocols**,
including **native CAN socket device support**, with current, production-ready support for
**J1939** and **NMEA 2000 (N2K)**.

The project is structured as a **multi-module Maven build**, allowing protocol layers to evolve
independently while sharing a common CAN framing and device-access core.

---

## What This Project Is

- A **CAN bus protocol stack**, not just parsers
- Includes **SocketCAN-based device access**
- Designed to sit inside larger systems (gateways, brokers, analytics pipelines)
- Transport-aware but not UI- or application-level

This is not a CAN driver replacement. It builds on OS-level CAN support and focuses on
**protocol correctness, structure, and extensibility**.

---

## Module Overview

### `canbus-core`
Core CAN and protocol framing layer.

Responsibilities:
- Native **CAN socket (SocketCAN) device support**
- Construction and parsing of **29-bit extended CAN identifiers**
- Shared framing utilities for J1939 and N2K
- Transport-facing abstractions used by higher-level protocols

This module owns the boundary between the operating system CAN interface and protocol logic.

---

### `canbus-j1939`
J1939 protocol implementation.

Responsibilities:
- J1939 identifier construction and decoding
- PDU1 vs PDU2 handling
- PGN, priority, source, and destination resolution
- Protocol-correct framing independent of transport

This module implements **core SAE J1939 semantics**, not vendor-specific extensions.

#### Dialect Support (Planned)
J1939 is commonly extended by OEMs and industries. Planned work includes:

- Industry-specific PGN catalogs
- OEM extensions
- Runtime-selectable J1939 dialects

The intent is to support dialects via **pluggable definitions**, avoiding hard-coded logic.

---

### `canbus-nmea2000`
NMEA 2000 (N2K) protocol implementation.

Responsibilities:
- Loading and interpreting NMEA 2000 XML definitions
- Mapping PGNs to structured field models
- Converting N2K messages to and from JSON
- Schema generation and validation support

Dependencies:
- `canbus-core`
- `canbus-j1939`

NMEA 2000 is treated as a **specialised J1939 profile**, preserving correct layering and reuse.

Want to dive deeper? 
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Maps-Messaging/canbus_interface)
---

## Current Capabilities

- SocketCAN device access
- J1939 frame construction and decoding
- NMEA 2000 message parsing and formatting
- JSON conversion and schema-driven validation
- Multi-module Maven build with clear protocol separation

---

## Planned Work

### CANopen Support
A future module is planned:

```
canbus-canopen
```

Expected scope:
- CANopen object dictionary handling
- PDO / SDO framing
- Network management (NMT) states
- Profile support (CiA 301, CiA 402)

CANopen is intentionally separate due to its significantly different protocol model.

---

### J1939 Dialect Expansion
Future development will focus on:

- Multiple concurrent dialects
- Vendor- and industry-specific PGN mappings
- Use in gateways, sniffers, and normalisation pipelines

---

## Build

Standard Maven multi-module build.

```
mvn clean install
```

Snapshots:
```
mvn clean deploy
```

Releases (Maven Central):
```
mvn clean deploy -Prelease
```

---

## Design Principles

- **Explicit layering**: CAN socket → framing → protocol → profile
- **Protocol correctness first**
- **No vendor lock-in**
- **Transport-aware, application-agnostic**
- **Extensible by construction**

---

## License

Licensed under the **Apache License, Version 2.0**, with the **Commons Clause**.

See the LICENSE file for details.
