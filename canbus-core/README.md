# SocketCAN Interface Layer (Java / JNA)

This repository provides a **thin, honest Java interface** over Linux
**SocketCAN** using **JNA**.

It deliberately does **one thing**: - Open a CAN interface - Read and
write raw CAN frames - Detect Classic CAN vs CAN FD capability - Expose
*capabilities*, not opinions

It does **not**: - Fragment messages - Implement NMEA2000, J1939,
ISO-TP, or any other protocol - Invent transport semantics - Hide kernel
reality

If you want those things, they belong **above** this layer.

------------------------------------------------------------------------

## Design Goals

-   **Thin**: minimal abstraction over the OS
-   **Truthful**: never claims support for something it cannot do
-   **Deterministic**: no guessing, no heuristics
-   **Protocol-agnostic**: frames in, frames out

This layer is intended to sit at the same conceptual level as TCP/UDP
socket adapters in larger systems, except CAN does not pretend to be a
stream.

------------------------------------------------------------------------

## Supported Features

-   Linux **SocketCAN** (`can0`, `can1`, etc.)
-   Classic CAN frames (0--8 byte payload)
-   CAN FD frames (0--64 byte payload), when:
    -   the interface is configured for FD
    -   the socket allows FD frames
-   Runtime capability detection
-   Blocking read/write semantics
-   No fragmentation or reassembly

------------------------------------------------------------------------

## Classic CAN vs CAN FD

This layer detects capabilities at runtime:

Capability             How it is detected
  ---------------------- -------------------------------------
Interface FD support   `/sys/class/net/<if>/mtu` (72 → FD)
Socket FD enabled      `getsockopt(CAN_RAW_FD_FRAMES)`
I/O max payload        Derived from interface + socket

The result is exposed via `CanCapabilities`, which is the **single
source of truth**.

------------------------------------------------------------------------

## CanCapabilities

``` java
public record CanCapabilities(
    boolean interfaceFdEnabled,
    boolean socketFdEnabled,
    int interfaceMaxPayloadBytes,
    int ioMaxPayloadBytes
)
```

Interpretation: - `interfaceMaxPayloadBytes`: what the OS + hardware
support (8 or 64) - `ioMaxPayloadBytes`: what this implementation can
actually read/write - FD is usable **only if** all relevant flags align

No additional `canSend()`, `canRead()`, or helper methods are provided.
Higher layers can derive behaviour directly from this record.

------------------------------------------------------------------------

## What This Layer Does *Not* Do

This is intentional:

-   ❌ No message fragmentation
-   ❌ No multi-frame reassembly
-   ❌ No PGN parsing
-   ❌ No address semantics
-   ❌ No retry logic
-   ❌ No protocol awareness

CAN is a **broadcast frame bus**. Anything above that is someone else's
problem.

------------------------------------------------------------------------

## Intended Usage

Typical usage pattern:

1.  Open `SocketCanDevice`
2.  Query `CanCapabilities`
3.  Read and write `CanFrame`
4.  Let higher layers handle meaning, framing, and protocol rules

This layer is suitable as a foundation for: - NMEA 2000 - J1939 -
ISO-TP - Custom CAN-based protocols - Diagnostic or monitoring tools

------------------------------------------------------------------------

## Platform Requirements

-   Linux
-   SocketCAN enabled kernel
-   CAN or CAN FD capable interface
-   Java + JNA

This is **Linux-only by design**.

------------------------------------------------------------------------

## Philosophy

If this layer ever starts to feel "helpful", it has gone too far.

It exists to expose the bus as it is, not as we wish it were.
