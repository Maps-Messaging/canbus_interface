# SocketCAN Interface Layer (Java / JNA)

This module provides a **thin, honest Java interface** over Linux
**SocketCAN** using **JNA**.

It deliberately does **one thing**:

- Open a CAN interface
- Read and write raw CAN frames
- Detect Classic CAN vs CAN FD capability
- Expose *capabilities*, not opinions

It deliberately does **not**:

- Fragment messages
- Implement NMEA2000, J1939, ISO-TP, or any other protocol
- Invent transport semantics
- Hide kernel reality

If you want those things, they belong **above** this layer.

---

## Design Goals

- **Thin**: minimal abstraction over the OS
- **Truthful**: never claims support for something it cannot do
- **Deterministic**: no guessing, no heuristics
- **Protocol-agnostic**: frames in, frames out

This layer sits at the same conceptual level as TCP/UDP socket adapters,
except CAN does not pretend to be a stream.

---

## Supported Features

- Linux **SocketCAN** (`can0`, `can1`, `vcan0`, etc.)
- Classic CAN frames (0–8 byte payload)
- CAN FD frames (0–64 byte payload), when:
    - the interface is configured for FD
    - the socket allows FD frames
- Runtime capability detection
- Blocking read/write semantics
- Explicit error reporting with native `errno`
- No fragmentation or reassembly

---

## Classic CAN vs CAN FD

Capabilities are detected at runtime:

| Capability | How it is detected |
|-----------|--------------------|
| Interface FD support | `/sys/class/net/<if>/mtu` (72 → FD) |
| Socket FD enabled | `getsockopt(CAN_RAW_FD_FRAMES)` |
| I/O max payload | Derived from interface + socket |

The result is exposed via `CanCapabilities`, which is the **single source of truth**.

---

## CanCapabilities

```java
public record CanCapabilities(
    boolean interfaceFdEnabled,
    boolean socketFdEnabled,
    int interfaceMaxPayloadBytes,
    int ioMaxPayloadBytes
)
```

Interpretation:

- `interfaceMaxPayloadBytes`  
  What the OS + hardware support (8 or 64)

- `ioMaxPayloadBytes`  
  What this implementation can actually read/write

CAN FD is usable **only if** all relevant flags align.

No helper methods such as `canSend()`, `canRead()`, or `supportsFd()` are
provided. Higher layers derive behaviour directly from this record.

---

## Philosophy

If this layer ever starts to feel *helpful*, it has gone too far.

It exists to expose the bus as it is,  
not as we wish it were.
