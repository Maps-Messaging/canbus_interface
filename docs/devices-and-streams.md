# Devices and streams

Use `canbus-core` when the application needs raw CAN frame access without protocol decoding.
`CanDevice` offers `readFrame()`, `writeFrame(CanFrame)`, `writeFrames(List<CanFrame>)`, `flush()`, `getCanCapabilities()`, and `close()`.
The base `writeFrames` implementation writes sequentially; a batch is not a bus transaction and has no rollback.
The optional `QueuedCanDevice` changes the write path to asynchronous queue submission.

## Frame representation

```java
CanFrame standard = new CanFrame(0x123, false, 3, new byte[] {0x01, 0x23, (byte) 0xAB});
CanFrame extended = new CanFrame(0x18FEF100, true, 2, new byte[] {0x45, 0x67});
```

`CanFrame` is in `io.mapsmessaging.canbus.device.frames`.
Pass a clean identifier: `0..0x7FF` for standard frames or `0..0x1FFFFFFF` for extended frames, with `extendedFrame` selecting the format.
Do not OR in Linux `CAN_EFF_FLAG`, `CAN_RTR_FLAG`, or `CAN_ERR_FLAG`; SocketCAN adds/removes its own extended-frame flag.
The model does not represent remote-request or error frames. SocketCAN rejects received RTR/ERR flags; Waveshare rejects remote frames;
the SLCAN reader accepts only `t` and `T` data frames.

Despite its name, `dataLengthCode` is a payload byte count, from 0 through 64, rather than the encoded four-bit CAN FD DLC.
The constructor requires enough data, copies only that count, and `data()` returns a defensive copy.
A zero-length frame accepts `null` or an empty array, but not nonempty data.
`isCanFd()` means the count exceeds eight: the model does not preserve a distinct FD format for short frames or expose BRS/ESI flags.

`getRawData()` / `CanFrame.fromBytes(...)` use a library-specific 13-byte classic format:
four big-endian identifier bytes, one flags byte (`bit 0 = extended`, `bits 1..4 = length`), and eight payload/padding bytes.
This is neither Linux `struct can_frame` nor either adapter's stream format. It cannot encode CAN FD.

## Linux SocketCAN

Construct `new SocketCanDevice("can0")` after the operating system has configured and brought up that interface.
It binds a raw CAN socket through JNA; it does not create the interface, set bitrate, or configure the controller.
`readFrame()` blocks in the native read. No public receive timeout, polling, or filter API is provided.
Use a single reader per instance: the read path reuses native buffers. Plan application shutdown around blocking reads;
this API does not promise that interrupting a Java thread or closing from another thread cancels a native read immediately.

[SocketCanExample.java](../examples/java/SocketCanExample.java) opens `vcan0` by default and reads one frame without transmitting.
It accepts another interface name for receiving. Its explicit `--send-vcan` mode sends one classic frame only when the name is `vcan0`.
The sending mode exits without trying to receive its own frame; observe it with a separate socket such as `candump`.
See [example build/run instructions](../examples/README.md).

Create a dedicated Linux virtual CAN interface for testing, if `vcan0` does not already exist:

```sh
sudo modprobe vcan
sudo ip link add dev vcan0 type vcan
sudo ip link set dev vcan0 up
ip -details link show dev vcan0
```

These administrative commands require the kernel module and appropriate privileges. They are setup commands, not Java example prerequisites on a physical CAN interface.
Do not substitute a physical interface for the example's virtual transmit path. For a receiver demonstration, start the Java example and send from another terminal:

```sh
cansend vcan0 123#0123AB
```

`cansend` and `candump` belong to `can-utils`. To observe explicit example transmission, start `candump vcan0` and then run `SocketCanExample vcan0 --send-vcan`.
Remove only the virtual interface you created when finished: `sudo ip link delete dev vcan0`.
For the existing Raspberry Pi dual MCP2515 hardware setup, see [canbus-core/pi_setup.md](../canbus-core/pi_setup.md).

The native backend requires Linux CAN sockets and Linux sysfs. `readInterfaceStatus()` additionally invokes `ip` from a standard system directory.
Opening native SocketCAN on Windows or macOS is not supported by this implementation.

### Capabilities and CAN FD

`CanCapabilities` reports `interfaceFdEnabled`, `socketFdEnabled`, `interfaceMaxPayloadBytes`, and `ioMaxPayloadBytes`.
`canSendFd()` requires both FD booleans and an effective I/O payload limit of 64 bytes.
Check this per opened device rather than assuming that a controller advertised as CAN FD gives the application FD support.

The current SocketCAN constructor reads `/sys/class/net/<name>/mtu` (72 indicates FD) and queries `CAN_RAW_FD_FRAMES` with `getsockopt`.
It does not enable that socket option. Interface FD support alone is therefore insufficient; do not expect the ordinary constructor to enable FD transmission.
Writes of 0–8 bytes use classic frames; larger writes require both FD capabilities and set native FD flags to zero.
The API supplies no explicit BRS option and cannot preserve all native FD metadata on receive.
These are current implementation limits, not instructions to bypass capability checks.

## Stream-backed adapters

The stream device is `SerialCanDevice`, in `io.mapsmessaging.canbus.device`:

```java
SerialCanDevice device = new SerialCanDevice("adapter-name", inputStream, outputStream, codec);
```

Here `inputStream` and `outputStream` are streams already opened by the application's chosen transport, and `codec` is a `CanFrameStreamCodec`.
No `StreamCanDevice`, stream configuration bean, serial-port discovery API, or serial dependency is supplied in this revision.
Serial baud, parity, stop bits, flow control, read deadlines, and port access permissions belong to the application's serial transport.
Serial line baud and CAN bus bitrate are separate settings; choose both to match the particular adapter and firmware.
The same stream interface can also wrap another byte transport when it carries the selected adapter protocol.

For example, after the application opens and configures its serial port, a helper can transfer its streams to the device:

```java
static SerialCanDevice attachAdapter(InputStream inputStream, OutputStream outputStream) throws IOException {
  return new SerialCanDevice("usb-can", inputStream, outputStream, new WaveshareUsbCanAStreamCodec());
}
```

The helper requires imports from `java.io`, `io.mapsmessaging.canbus.device`, and `io.mapsmessaging.canbus.device.codec.impl`.
The caller retains responsibility for closing the serial-port handle according to its transport library's contract.
Do not close the streams while the device is still using them, and do not let another reader consume protocol bytes from the same input stream.

On successful construction, `SerialCanDevice` retains both streams and calls `codec.initialise(inputStream, outputStream)`.
Its `close()` calls the codec close hook and then attempts to close both streams even if an earlier close fails; later failures are suppressed on the first exception.
The caller must clean up already opened streams if device construction fails, including failure in a custom codec's initialization hook.
Use try-with-resources around a successfully constructed device. If a queued wrapper owns it, close the wrapper rather than independently closing its delegate.

`SerialCanDevice.readFrame()` delegates directly to the codec; timeouts come from the underlying streams, not from this device API.
EOF conventions differ: SLCAN returns `null` when no further line exists; Waveshare throws `EOFException` when another required byte is absent.
A blocking input stream may wait indefinitely for a complete line or packet. Treat truncated/invalid packets according to your application's disconnect policy.
`writeFrame` delegates to the codec and `flush()` calls its flush hook. Flush buffered transport output when immediate handoff is required.

### Bundled framing protocols

| Codec | Encoding and limits | Adapter setup behavior |
| --- | --- | --- |
| `SlcanCanFrameStreamCodec` | ASCII `t`/`T`, hexadecimal ID/data, CR terminator; classic 0–8 bytes, no timestamps. | Explicit bitrate/open/close helpers; see below. |
| `WaveshareUsbCanAStreamCodec` | Binary `0xAA`, type/length, little-endian ID, payload, `0x55`; classic 0–8 bytes. | No bitrate/mode command API. |

Both report `CanCapabilities.classicCan()`; do not send FD frames through them.
In particular, the current SLCAN writer does not reject all lengths above eight even though its reader/capabilities are classic-only.
Validate against the reported limit before writing; an apparently successful encode is not proof that an adapter supports that frame.

For SLCAN, adapter initialization is explicit and firmware dependent. For an adapter supporting the supplied command set,
with the serial port already configured and a closed CAN channel, the calls are:

```java
SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
codec.setBitRate(outputStream, SlcanBitRate.CAN_250K);
codec.open(outputStream);
```

Use `codec.close(outputStream)` when shutting down that adapter channel before its stream is closed.
This one-argument method sends `C\r`; it is not the two-stream lifecycle close hook used by `SerialCanDevice`.
Wrapping this codec in `SerialCanDevice` does not automatically send SLCAN bitrate/open/close commands or validate command acknowledgments.
The SLCAN frame reader does not handle adapter command responses as a separate protocol; the application must manage those responses and setup sequence.

[StreamCodecExample.java](../examples/java/StreamCodecExample.java) performs two classic frame round trips using each codec and only memory streams.
It needs no serial library, hardware, operating-system interface, or adapter setup. It compares decoded frame contents and fails on a mismatch.
This verifies framing examples; it does not establish compatibility with arbitrary devices marketed as SLCAN or Waveshare adapters.

## Queued writes, overload, and shutdown

`QueuedCanDevice` wraps a `CanDevice`, starts a daemon writer thread immediately, and owns closing the delegate.
Reads pass straight through; this adds no receive queue or read timeout.
A complete example configuration expression is:

```java
QueuedCanDevice queued = new QueuedCanDevice(device, 128, 250_000, 20.0, QueueFullPolicy.REJECT_NEW, 25L);
```

The constructor values are queue depth, estimated bitrate in bit/s, maximum estimated bus usage percent, full-queue policy, and write-failure backoff in milliseconds.
The one-argument constructor defaults to 128 entries, 250000 bit/s, 20%, `DROP_OLDEST`, and 25 ms backoff.
These values control pacing only; they do not configure the hardware bitrate or measure actual bus utilization.
The estimate uses frame sizes and a stuffing factor; do not interpret it as a CAN FD arbitration/data-rate model or an enforced physical bus utilization limit.

Each `writeFrame` enqueues one message; `writeFrames` enqueues one batch of frames as one queue entry.
The writer sends the frames of a batch in order before dequeuing another batch, but bus arbitration and other senders still apply.
The queue depth counts queued messages/batches, not frames, and excludes an entry already being processed.
`getPendingWriteCount()` also includes a dequeued message until its processing attempt ends.

| Full-queue policy | Submission behavior |
| --- | --- |
| `REJECT_NEW` | Rejects the new message immediately with `IOException`; increments rejected count. |
| `DROP_OLDEST` | Removes oldest queued messages until the new message fits; increments dropped count. |

There is no policy that blocks until capacity becomes available and no timed enqueue method.
Successful submission means accepted into the queue, not written on the bus or acknowledged by a remote node.
The writer records I/O/runtime failures in `getStats().getWriteFailureCount()` and `getStats().getLastWriteException()`;
it does not replay a failed message after the backoff. A partly written batch can remain partly delivered.
Counters count messages/batches rather than individual frames. An empty queue alone does not mean its in-flight write has finished.

The writer flushes the delegate when the queue becomes empty. Calling `queued.flush()` does not provide a drain barrier:
the wrapper inherits `CanDevice`'s no-op flush default.
There is no await-drained method, per-message completion future, or write deadline.
Applications requiring a shutdown deadline must supervise pending writes and failures with their own bounded waiting policy and decide what to do on timeout.
Even zero pending writes does not prove remote delivery; consult the failure counters as well.

`close()` rejects future submissions, requests the writer to stop, interrupts it, waits up to 2 s, and closes the delegate.
It does not promise to drain queued messages, and a blocked delegate operation can outlast the join timeout.
Close the queued device once through its owner and coordinate producer shutdown before closing it.
