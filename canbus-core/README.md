# canbus-core

`canbus-core` supplies raw CAN frames, Linux SocketCAN access, CAN adapter stream codecs, and an optional queued writer.
It is usable independently of the J1939, NMEA 2000, event, and MapsMessaging server layers.

Start with the [device and stream integration guide](../docs/devices-and-streams.md) for constructors, ownership, frame identifiers,
capabilities, serial adapter setup, queue policies, and Linux virtual CAN setup.
The [example instructions](../examples/README.md) cover building and running the complete Java examples:

- [SocketCanExample.java](../examples/java/SocketCanExample.java): receive one frame; explicitly send a test frame only on `vcan0`.
- [StreamCodecExample.java](../examples/java/StreamCodecExample.java): encode/decode classic frames in memory with both bundled codecs.

## API map

| Type | Purpose |
| --- | --- |
| `CanFrame` | Validated standard/extended identifier, payload length, and defensively copied data. |
| `CanDevice` | Blocking frame read/write interface with capability reporting, flush, and close. |
| `SocketCanDevice` | Native Linux CAN socket accessed using JNA. The operating system configures the interface. |
| `SerialCanDevice` | Uses application-provided input/output streams and a `CanFrameStreamCodec`; owns their close lifecycle. |
| `SlcanCanFrameStreamCodec` | Classic SLCAN `t`/`T` data frames and explicit adapter command helpers. |
| `WaveshareUsbCanAStreamCodec` | Classic Waveshare USB-CAN-A `0xAA`/`0x55` binary framing. |
| `QueuedCanDevice` | Bounded asynchronous write queue over another device, with estimated bandwidth limiting and failure statistics. |

The stream codecs frame bytes; they do not open or configure a serial port.
The application selects a serial library, port settings, adapter mode, timeouts, and CAN bitrate where required.
There is no `StreamCanDevice` or `StreamCanDeviceConfig` API in this revision; the stream-backed implementation is `SerialCanDevice`.

Classic CAN payloads contain 0–8 bytes. The frame model also accepts up to 64 bytes, but each backend determines usable CAN FD support.
Read `getCanCapabilities()` and the guide's limitations before attempting CAN FD; the bundled stream codecs report classic CAN only.
The core module does not parse PGNs, fragment messages, interpret payloads, or provide delivery acknowledgments and retries.

SocketCAN requires Linux, kernel CAN support, a configured interface, and JNA's native runtime.
Stream codec logic uses Java streams; actual serial access on another operating system depends on the application's serial transport and adapter driver.
No cross-platform hardware compatibility is implied by the stream API.

For the existing dual MCP2515 Raspberry Pi installation instructions, see [pi_setup.md](pi_setup.md).
