/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.mapsmessaging.canbus.device;


/**
 * SocketCAN device wrapper backed by Linux {@code AF_CAN} raw sockets.
 *
 * <p>This class provides low-level access to a CAN or CAN-FD network interface
 * using native Linux SocketCAN APIs via JNA. It supports:</p>
 *
 * <ul>
 *   <li>Classic CAN frames (up to 8 bytes payload)</li>
 *   <li>CAN FD frames (up to 64 bytes payload), when enabled at both interface
 *       and socket level</li>
 *   <li>Blocking read and write of frames</li>
 *   <li>Basic interface status inspection via {@code ip -json link show}</li>
 * </ul>
 *
 * <p>Error handling is explicit and native error codes ({@code errno}) are
 * included in exception messages where available.</p>
 *
 * <p>This class is intended for Linux environments with SocketCAN support.
 * Unit tests bypass constructor execution and native calls; integration tests
 * should be run against {@code vcan} or real CAN hardware.</p>
 */

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import io.mapsmessaging.canbus.device.frames.NativeCanFdFrame;
import io.mapsmessaging.canbus.device.frames.NativeCanFrame;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;


public final class SocketCanDevice implements Closeable {

  private final LibCFacade libC;

  private static final int AF_CAN = 29;
  private static final int SOCK_RAW = 3;
  private static final int CAN_RAW = 1;

  private static final int SIOCGIFINDEX = 0x8933;

  private static final int SOL_CAN_RAW = 101;
  private static final int CAN_RAW_FD_FRAMES = 5;

  private static final int CLASSIC_CAN_MAX_PAYLOAD = 8;
  private static final int CAN_FD_MAX_PAYLOAD = 64;

  private static final int CANFD_MTU = 72;

  private final int socketFileDescriptor;
  private final String interfaceName;

  @Getter
  private final CanCapabilities canCapabilities;

  /**
   * Creates a SocketCAN device bound to the specified interface using
   * the default native libc implementation.
   *
   * @param interfaceName the SocketCAN interface name (e.g. {@code can0}, {@code vcan0})
   * @throws IOException if the socket cannot be created, bound, or configured
   */
  public SocketCanDevice(String interfaceName) throws IOException {
    this(interfaceName, new JnaLibCFacade(), null);
  }

  /**
   * Creates a SocketCAN device bound to the specified interface using
   * injected native and interface resolution components.
   *
   * <p>This constructor exists primarily to support testing and controlled
   * environments.</p>
   *
   * @param interfaceName the SocketCAN interface name
   * @param libC native libc facade used for system calls
   * @param resolver interface index resolver; if {@code null}, a default
   *                 JNA-based resolver is used
   * @throws IOException if the socket cannot be created, bound, or configured
   */
  public SocketCanDevice(String interfaceName, LibCFacade libC, InterfaceIndexResolver resolver) throws IOException {
    this.interfaceName = interfaceName;
    this.libC = libC;
    InterfaceIndexResolver interfaceIndexResolver = resolver != null ? resolver : new JnaInterfaceIndexResolver(libC);

    int fileDescriptor = libC.socket(AF_CAN, SOCK_RAW, CAN_RAW);
    if (fileDescriptor < 0) {
      throw new IOException("socket(AF_CAN,SOCK_RAW,CAN_RAW) failed errno=" + libC.getLastError());
    }

    int interfaceIndex = interfaceIndexResolver.resolveInterfaceIndex(fileDescriptor, interfaceName);

    SockAddrCan socketAddress = new SockAddrCan();
    socketAddress.canFamily = (short) AF_CAN;
    socketAddress.canInterfaceIndex = interfaceIndex;
    socketAddress.address = new byte[8];
    socketAddress.write();
    int bindResult = libC.bind(fileDescriptor, socketAddress, socketAddress.size());
    if (bindResult != 0) {
      int errno = Native.getLastError();
      libC.close(fileDescriptor);
      throw new IOException("bind(" + interfaceName + ") failed errno=" + errno);
    }

    this.socketFileDescriptor = fileDescriptor;
    this.canCapabilities = loadCapabilities();
  }

  /**
   * Reads a single CAN or CAN FD frame from the socket.
   *
   * <p>The method automatically detects whether the incoming frame is
   * a classic CAN frame or a CAN FD frame based on the number of bytes read.</p>
   *
   * @return the decoded {@link CanFrame}
   * @throws IOException if the read fails, an invalid frame is received,
   *                     or an unexpected frame size is encountered
   */
  public CanFrame readFrame() throws IOException {
    NativeCanFrame classicProbe = new NativeCanFrame();
    NativeCanFdFrame fdProbe = new NativeCanFdFrame();

    int classicSize = classicProbe.size();
    int fdSize = fdProbe.size();

    Pointer buffer = new Memory(fdSize);

    int bytesRead = libC.read(this.socketFileDescriptor, buffer, fdSize);
    if (bytesRead < 0) {
      throw new IOException("read(can_frame/canfd_frame) failed errno=" + libC.getLastError());
    }

    if (bytesRead == classicSize) {
      NativeCanFrame classic = new NativeCanFrame(buffer);
      classic.read();

      int canIdentifier = classic.canIdentifier;
      int dataLengthCode = classic.dataLengthCode & 0xFF;

      if (dataLengthCode > CLASSIC_CAN_MAX_PAYLOAD) {
        throw new IOException("Invalid classic DLC: " + dataLengthCode);
      }

      byte[] data = Arrays.copyOf(classic.data, CLASSIC_CAN_MAX_PAYLOAD);
      return new CanFrame(canIdentifier, dataLengthCode, data);
    }

    if (bytesRead == fdSize) {
      NativeCanFdFrame fd = new NativeCanFdFrame(buffer);
      fd.read();

      int canIdentifier = fd.canIdentifier;
      int dataLengthCode = fd.length & 0xFF;

      if (dataLengthCode > CAN_FD_MAX_PAYLOAD) {
        throw new IOException("Invalid FD length: " + dataLengthCode);
      }

      byte[] data = Arrays.copyOf(fd.data, CAN_FD_MAX_PAYLOAD);
      return new CanFrame(canIdentifier, dataLengthCode, data);
    }

    throw new IOException("Unexpected read size: " + bytesRead + " bytes (expected " + classicSize + " or " + fdSize + ")");
  }

  /**
   * Writes a CAN or CAN FD frame to the socket.
   *
   * <p>The frame type (classic vs FD) is determined by the frame's
   * data length code.</p>
   *
   * @param frame the frame to write
   * @throws IOException if the native write fails or writes fewer bytes
   *                     than expected
   * @throws IllegalArgumentException if the frame is invalid or violates
   *                                  CAN/CAN-FD constraints
   */
  public void writeFrame(CanFrame frame) throws IOException {
    writeFrame(frame.canIdentifier(), frame.dataLengthCode(), frame.data());
  }

  /**
   * Writes a CAN or CAN FD frame to the socket using raw parameters.
   *
   * <p>Classic CAN frames are used for payloads up to 8 bytes. CAN FD frames
   * require both interface-level and socket-level FD support.</p>
   *
   * @param canIdentifier CAN identifier (standard or extended, as provided)
   * @param dataLengthCode number of payload bytes
   * @param data payload data; must contain at least {@code dataLengthCode} bytes
   * @throws IOException if the native write fails or writes fewer bytes
   *                     than expected
   * @throws IllegalArgumentException if parameters are invalid or CAN FD
   *                                  is not enabled when required
   */
  public void writeFrame(int canIdentifier, int dataLengthCode, byte[] data) throws IOException {
    if (data == null) {
      throw new IllegalArgumentException("data must not be null");
    }
    if (dataLengthCode < 0) {
      throw new IllegalArgumentException("dataLengthCode must be >= 0");
    }
    if (dataLengthCode > CAN_FD_MAX_PAYLOAD) {
      throw new IllegalArgumentException("dataLengthCode must be 0.." + CAN_FD_MAX_PAYLOAD);
    }
    if (data.length < dataLengthCode) {
      throw new IllegalArgumentException("data.length (" + data.length + ") < dataLengthCode (" + dataLengthCode + ")");
    }

    if (dataLengthCode <= CLASSIC_CAN_MAX_PAYLOAD) {
      NativeCanFrame classic = new NativeCanFrame();
      classic.canIdentifier = canIdentifier;
      classic.dataLengthCode = (byte) dataLengthCode;

      Arrays.fill(classic.data, (byte) 0);
      System.arraycopy(data, 0, classic.data, 0, dataLengthCode);

      classic.write();

      int bytesWritten = libC.write(this.socketFileDescriptor, classic.getPointer(), classic.size());
      if (bytesWritten < 0) {
        throw new IOException("write(can_frame) failed errno=" + libC.getLastError());
      }
      if (bytesWritten != classic.size()) {
        throw new IOException("Short write: " + bytesWritten + " bytes (expected " + classic.size() + ")");
      }
      return;
    }

    if (!canCapabilities.interfaceFdEnabled() || !canCapabilities.socketFdEnabled()) {
      throw new IllegalArgumentException("CAN FD not enabled for interface/socket; cannot send " + dataLengthCode + " bytes");
    }

    NativeCanFdFrame fd = new NativeCanFdFrame();
    fd.canIdentifier = canIdentifier;
    fd.length = (byte) dataLengthCode;
    fd.flags = 0;

    Arrays.fill(fd.data, (byte) 0);
    System.arraycopy(data, 0, fd.data, 0, dataLengthCode);

    fd.write();

    int bytesWritten = libC.write(this.socketFileDescriptor, fd.getPointer(), fd.size());
    if (bytesWritten < 0) {
      throw new IOException("write(canfd_frame) failed errno=" + libC.getLastError());
    }
    if (bytesWritten != fd.size()) {
      throw new IOException("Short write: " + bytesWritten + " bytes (expected " + fd.size() + ")");
    }
  }

  /**
   * Reads the current operational status of the CAN interface.
   *
   * <p>This method executes {@code ip -details -statistics -json link show}
   * for the configured interface and parses the result.</p>
   *
   * @return the parsed {@link CanInterfaceStatus}
   * @throws IOException if the command fails or the output cannot be parsed
   */
  public CanInterfaceStatus readInterfaceStatus() throws IOException {
    String jsonText = runIpJsonLinkShow(this.interfaceName);

    JsonElement parsed = JsonParser.parseString(jsonText);
    if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
      throw new IOException("Invalid ip -json output for interface " + interfaceName);
    }

    JsonObject link = parsed.getAsJsonArray().get(0).getAsJsonObject();

    CanInterfaceStatus status = new CanInterfaceStatus();
    status.setInterfaceName(getString(link, "ifname"));
    status.setOperState(getString(link, "operstate"));
    status.setMtu(getInteger(link, "mtu"));

    JsonObject linkInfo = getObject(link, "linkinfo");
    JsonObject infoData = linkInfo != null ? getObject(linkInfo, "info_data") : null;

    if (infoData != null) {
      status.setCanState(getString(infoData, "state"));
      status.setRestartMs(getInteger(infoData, "restart_ms"));

      JsonObject bittiming = getObject(infoData, "bittiming");
      if (bittiming != null) {
        status.setBitrate(getInteger(bittiming, "bitrate"));
      }
    }

    return status;
  }

  /**
   * Closes the underlying SocketCAN file descriptor.
   *
   * @throws IOException if the close operation fails
   */
  @Override
  public void close() throws IOException {
    int result = libC.close(this.socketFileDescriptor);
    if (result != 0) {
      int errno = libC.getLastError();
      throw new IOException("close() failed errno=" + errno);
    }
  }

  /**
   * Determines CAN and CAN FD capabilities of the interface and socket.
   *
   * @return resolved CAN capability information
   * @throws IOException if capability detection fails
   */
  private CanCapabilities loadCapabilities() throws IOException {
    boolean interfaceFdEnabled = isInterfaceFdEnabled();
    boolean socketFdEnabled = isSocketFdEnabled();

    int interfaceMaxPayloadBytes = interfaceFdEnabled ? CAN_FD_MAX_PAYLOAD : CLASSIC_CAN_MAX_PAYLOAD;

    int ioMaxPayloadBytes;
    if (interfaceFdEnabled && socketFdEnabled) {
      ioMaxPayloadBytes = CAN_FD_MAX_PAYLOAD;
    } else {
      ioMaxPayloadBytes = CLASSIC_CAN_MAX_PAYLOAD;
    }

    return new CanCapabilities(
        interfaceFdEnabled,
        socketFdEnabled,
        interfaceMaxPayloadBytes,
        ioMaxPayloadBytes
    );
  }

  /**
   * Determines whether CAN FD is enabled at the interface level.
   *
   * <p>This is inferred from the interface MTU value.</p>
   *
   * @return {@code true} if CAN FD is enabled on the interface
   * @throws IOException if the MTU cannot be read or parsed
   */
  private boolean isInterfaceFdEnabled() throws IOException {
    Path mtuPath = Path.of("/sys/class/net", interfaceName, "mtu");
    String mtuText = Files.readString(mtuPath, StandardCharsets.US_ASCII).trim();

    int mtu;
    try {
      mtu = Integer.parseInt(mtuText);
    } catch (NumberFormatException e) {
      throw new IOException("Invalid MTU for " + interfaceName + ": '" + mtuText + "'", e);
    }

    return mtu == CANFD_MTU;
  }

  /**
   * Determines whether CAN FD is enabled at the socket level.
   *
   * @return {@code true} if CAN FD is enabled for the socket
   * @throws IOException if the socket option query fails
   */
  private boolean isSocketFdEnabled() throws IOException {
    IntByReference value = new IntByReference(0);
    IntByReference length = new IntByReference(Integer.BYTES);

    int result = libC.getsockopt(this.socketFileDescriptor, SOL_CAN_RAW, CAN_RAW_FD_FRAMES, value, length);
    if (result != 0) {
      throw new IOException("getsockopt(CAN_RAW_FD_FRAMES) failed errno=" + libC.getLastError());
    }

    return value.getValue() != 0;
  }

  private static String runIpJsonLinkShow(String interfaceName) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(
        "ip",
        "-details",
        "-statistics",
        "-json",
        "link",
        "show",
        "dev",
        interfaceName
    );

    processBuilder.redirectErrorStream(true);

    Process process;
    try {
      process = processBuilder.start();
    } catch (IOException e) {
      throw new IOException("Failed to execute 'ip' command", e);
    }

    byte[] outputBytes;
    try (InputStream inputStream = process.getInputStream()) {
      outputBytes = inputStream.readAllBytes();
    }

    int exitCode;
    try {
      exitCode = process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for 'ip' command", e);
    }

    String outputText = new String(outputBytes, StandardCharsets.UTF_8).trim();

    if (exitCode != 0) {
      throw new IOException("'ip' command failed (exit=" + exitCode + "): " + outputText);
    }

    return outputText;
  }

  private static JsonObject getObject(JsonObject source, String key) {
    if (source == null) {
      return null;
    }
    JsonElement value = source.get(key);
    if (value == null || value.isJsonNull() || !value.isJsonObject()) {
      return null;
    }
    return value.getAsJsonObject();
  }

  private static String getString(JsonObject source, String key) {
    if (source == null) {
      return null;
    }
    JsonElement value = source.get(key);
    if (value == null || value.isJsonNull()) {
      return null;
    }
    if (!value.isJsonPrimitive()) {
      return null;
    }
    return value.getAsString();
  }

  private static Integer getInteger(JsonObject source, String key) {
    if (source == null) {
      return null;
    }
    JsonElement value = source.get(key);
    if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
      return null;
    }
    try {
      return value.getAsInt();
    } catch (Exception e) {
      return null;
    }
  }
}
