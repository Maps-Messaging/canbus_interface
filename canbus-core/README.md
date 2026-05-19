# CAN Bus Interface Layer (Java / JNA / Serial)

This repository provides a thin Java interface over CAN bus devices.

It supports native Linux SocketCAN interfaces and serial-attached CAN adapters, including the Waveshare USB-CAN dongle style devices.

The project deliberately focuses on device-level CAN frame access. It opens CAN interfaces, reads and writes raw CAN frames, detects available capabilities where the platform allows it, and exposes those capabilities without trying to invent protocol behaviour above the bus.

It does not implement higher-level CAN protocols, message fragmentation, transport semantics, routing, retries, or application-level meaning. Those belong above this layer.

---

## Design Goals

- **Thin**: minimal abstraction over the underlying device or operating system interface
- **Truthful**: exposes what the device and implementation can actually do
- **Deterministic**: no guessing, no hidden protocol behaviour
- **Protocol-agnostic**: frames in, frames out
- **Extensible**: supports different CAN device backends behind a common model

This layer sits at the same conceptual level as TCP, UDP, or serial adapters in larger systems. The difference is that CAN is a broadcast frame bus, not a stream, and this layer does not pretend otherwise.

---

## Supported Device Types

### SocketCAN

Native Linux SocketCAN interfaces such as:

- `can0`
- `can1`
- `vcan0`

SocketCAN support is provided using JNA and the Linux CAN socket API.

### Serial CAN Adapters

Serial-attached CAN devices are supported through a serial transport layer. This allows CAN frames to be exchanged with devices that expose a serial protocol rather than a native SocketCAN interface.

Current support includes Waveshare USB-CAN style adapters.

---

## Supported Features

- Linux SocketCAN support
- Virtual CAN support through `vcan` where configured by the operating system
- Serial CAN adapter support
- Waveshare USB-CAN adapter support
- Classic CAN frames with 0 to 8 byte payloads
- CAN FD frames where supported by the device and implementation
- Blocking read/write semantics
- Device capability reporting where available
- Raw frame read/write access
- No fragmentation or reassembly

---

## Classic CAN vs CAN FD

CAN capability depends on the selected backend and the underlying hardware.

For SocketCAN, capability detection can include interface-level checks such as the Linux network interface MTU and socket options.

For serial adapters, capability detection depends on what the adapter protocol exposes and what the implementation can verify safely.

The implementation should expose the effective I/O limits rather than assuming that all CAN devices behave the same way. Because, naturally, they do not.

---

## Capabilities

Device capability information is exposed through the CAN device abstraction so higher layers can decide what behaviour is valid.

Typical capability information includes:

- Whether the interface supports CAN FD
- Whether the active connection can send or receive CAN FD frames
- Maximum payload size supported by the interface
- Maximum payload size supported by the current implementation path

The capability model is the source of truth. Higher layers should derive behaviour from it rather than duplicating backend-specific checks.

---

## What This Layer Does Not Do

This is intentional:

- No message fragmentation
- No multi-frame reassembly
- No PGN parsing
- No address semantics
- No retry logic
- No protocol awareness
- No payload interpretation
- No transport-level fiction pretending CAN is TCP with smaller packets

CAN is a broadcast frame bus. Anything above raw frame exchange belongs in a protocol layer.

---

## Intended Usage

Typical usage pattern:

1. Configure the CAN device backend.
2. Open the device.
3. Query device capabilities.
4. Read and write CAN frames.
5. Let higher layers handle meaning, framing, addressing, and protocol rules.

This layer is suitable as a foundation for protocol implementations, diagnostic tools, monitoring utilities, replay tools, and embedded system integrations.

---

## Platform Notes

### SocketCAN

SocketCAN support requires:

- Linux
- SocketCAN-enabled kernel
- CAN, CAN FD, or virtual CAN interface
- Java
- JNA

### Serial CAN Devices

Serial CAN support requires:

- A supported serial CAN adapter
- A configured serial port
- Java serial support
- Correct adapter mode and baud configuration

Serial adapters are not SocketCAN devices. They are CAN devices accessed through a serial protocol, which means their behaviour is limited by both the CAN bus and the adapter firmware.

---

## Project Scope

This module provides the device-facing CAN layer.

Protocol-specific modules should be built above it. They should consume and produce CAN frames while keeping protocol rules out of this device abstraction.

That separation keeps the low-level device layer small, testable, and honest.

---

## Philosophy

If this layer starts interpreting payloads, managing protocol state, or trying to be helpful, it has gone too far.

It exists to expose CAN devices as they are, not as we wish hardware vendors had documented them.
