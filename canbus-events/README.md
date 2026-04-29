# CAN Bus Events

This Maven sub-project contains CAN bus event protocol support for MapsMessaging.

It sits above the raw CAN device layer. The device layer is responsible for opening CAN interfaces, reading and writing frames, and exposing device capabilities. This module is responsible for interpreting CAN frames as protocol-level events and converting those events into structures that the wider MapsMessaging server can route, transform, and publish.

At present, this module supports CANaerospace event handling.

---

## Purpose

CAN bus provides frames. It does not provide application meaning.

This module adds that missing interpretation layer for supported CAN-based event protocols. It turns protocol-specific CAN frames into usable event data while keeping the lower-level CAN device code clean and protocol-agnostic.

The split is deliberate:

- The CAN device layer handles interfaces, adapters, frames, and capabilities.
- This module handles supported CAN event protocols.
- The messaging server handles routing, filtering, schemas, persistence, and protocol bridging.

Because apparently one layer doing everything was not quite enough chaos for the industry.

---

## Supported Protocols

### CANaerospace

CANaerospace support is currently provided by this module.

The CANaerospace event handling layer is responsible for decoding CANaerospace frames into event data that can be processed by MapsMessaging. This allows CANaerospace traffic to be ingested and bridged into the server's normal message flow.

Typical uses include:

- Receiving CANaerospace frames from a CAN bus
- Decoding protocol fields from the CAN payload
- Producing structured event data
- Publishing decoded events into MapsMessaging topics
- Allowing downstream routing, filtering, transformation, and storage

---

## What This Module Does

- Provides protocol-level CAN event handling
- Decodes supported CAN frame formats
- Converts raw CAN payloads into meaningful event data
- Keeps protocol logic separate from device access
- Integrates decoded events with the MapsMessaging runtime

---

## What This Module Does Not Do

This module does not replace the CAN device layer.

It does not:

- Open SocketCAN interfaces directly
- Manage serial CAN adapters directly
- Detect hardware capabilities
- Implement generic CAN transport behaviour
- Pretend CAN is a stream
- Add reliability semantics that do not exist on the bus

Those responsibilities belong either below this module, in the CAN device layer, or above this module, in protocol-specific routing and application logic.

---

## Relationship to the CAN Device Layer

The CAN device layer provides raw frame access from sources such as SocketCAN, virtual CAN interfaces, and supported serial CAN adapters.

This module consumes those frames and applies supported event protocol rules.

In simple terms:

```text
CAN device layer  ->  raw CAN frames
canbus-events     ->  decoded CAN protocol events
MapsMessaging     ->  routing, filtering, bridging, persistence
```

This separation keeps the raw device implementation small and honest while allowing protocol support to grow independently.

---

## Maven Module Role

`canbus-events` is intended to be used as a protocol/event interpretation module within the wider CAN bus interface project.

It should contain code that understands CAN-based event formats and produces data suitable for MapsMessaging ingestion.

Device-specific code should remain outside this module unless it is strictly required for protocol interpretation.

---

## Design Principles

- Keep raw CAN access separate from protocol decoding
- Keep protocol decoding separate from routing policy
- Avoid hidden transport semantics
- Decode only supported protocols
- Expose event data clearly and predictably
- Keep the module extensible without documenting support that does not exist yet

---

## Typical Flow

1. A CAN device backend receives a raw CAN frame.
2. The frame is passed to the relevant event protocol handler.
3. The handler decodes the frame according to the supported protocol.
4. A structured event is produced.
5. MapsMessaging routes, filters, transforms, bridges, or stores the event.

---

## Scope

This module is the CAN event interpretation layer for MapsMessaging.

Current protocol support is CANaerospace.

Additional CAN-based event protocol support can be added here when implemented, without changing the purpose of the module or contaminating the raw CAN device layer with protocol knowledge.
