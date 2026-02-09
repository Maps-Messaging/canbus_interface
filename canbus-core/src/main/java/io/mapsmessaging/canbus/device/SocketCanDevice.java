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

  private static final int SOL_CAN_RAW = 101;
  private static final int CAN_RAW_FD_FRAMES = 5;

  private static final int CLASSIC_CAN_MAX_PAYLOAD = 8;
  private static final int CAN_FD_MAX_PAYLOAD = 64;

  private static final int CANFD_MTU = 72;

  // SocketCAN can_id flag bits and masks
  private static final int CAN_EFF_FLAG = 0x80000000;
  private static final int CAN_RTR_FLAG = 0x40000000;
  private static final int CAN_ERR_FLAG = 0x20000000;

  private static final int CAN_SFF_MASK = 0x000007FF;
  private static final int CAN_EFF_MASK = 0x1FFFFFFF;

  private final int socketFileDescriptor;
  private final String interfaceName;

  @Getter
  private final CanCapabilities canCapabilities;

  public SocketCanDevice(String interfaceName) throws IOException {
    this(interfaceName, new JnaLibCFacade(), null);
  }

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
    socketAddress.canFamily = ((short) AF_CAN);
    socketAddress.canInterfaceIndex = (interfaceIndex);
    socketAddress.address = (new byte[8]);
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

      int rawCanId = classic.canIdentifier;

      boolean extendedFrame = (rawCanId & CAN_EFF_FLAG) != 0;
      int cleanCanId = extendedFrame ? (rawCanId & CAN_EFF_MASK) : (rawCanId & CAN_SFF_MASK);

      // If you ever care about RTR/ERR, this is where you'd surface it.
      // For now, we reject them because your CanFrame model has no place to store them.
      if ((rawCanId & (CAN_RTR_FLAG | CAN_ERR_FLAG)) != 0) {
        throw new IOException("Unsupported CAN frame flags (RTR/ERR) in canIdentifier=0x" + Integer.toHexString(rawCanId));
      }

      int dataLengthCode = classic.dataLengthCode & 0xFF;
      if (dataLengthCode > CLASSIC_CAN_MAX_PAYLOAD) {
        throw new IOException("Invalid classic DLC: " + dataLengthCode);
      }

      byte[] data = Arrays.copyOf(classic.data, dataLengthCode);
      return new CanFrame(cleanCanId, extendedFrame, dataLengthCode, data);
    }

    if (bytesRead == fdSize) {
      NativeCanFdFrame fd = new NativeCanFdFrame(buffer);
      fd.read();

      int rawCanId = fd.canIdentifier;

      boolean extendedFrame = (rawCanId & CAN_EFF_FLAG) != 0;
      int cleanCanId = extendedFrame ? (rawCanId & CAN_EFF_MASK) : (rawCanId & CAN_SFF_MASK);

      if ((rawCanId & (CAN_RTR_FLAG | CAN_ERR_FLAG)) != 0) {
        throw new IOException("Unsupported CAN FD frame flags (RTR/ERR) in canIdentifier=0x" + Integer.toHexString(rawCanId));
      }

      int dataLengthCode = fd.length & 0xFF; // SocketCAN canfd_frame.len is byte length (0..64)
      if (dataLengthCode > CAN_FD_MAX_PAYLOAD) {
        throw new IOException("Invalid FD length: " + dataLengthCode);
      }

      byte[] data = Arrays.copyOf(fd.data, dataLengthCode);
      return new CanFrame(cleanCanId, extendedFrame, dataLengthCode, data);
    }

    throw new IOException("Unexpected read size: " + bytesRead + " bytes (expected " + classicSize + " or " + fdSize + ")");
  }

  public void writeFrame(CanFrame frame) throws IOException {
    writeFrame(frame.canIdentifier(), frame.extendedFrame(), frame.dataLengthCode(), frame.data());
  }

  public void writeFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) throws IOException {
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

    // Ensure caller isn't feeding us a "raw-with-flags" id.
    int maskedId = extendedFrame ? (canIdentifier & CAN_EFF_MASK) : (canIdentifier & CAN_SFF_MASK);
    if (maskedId != canIdentifier) {
      throw new IllegalArgumentException("canIdentifier contains invalid bits for " + (extendedFrame ? "extended" : "standard") + " frame: 0x" + Integer.toHexString(canIdentifier));
    }

    int rawCanId = extendedFrame ? (canIdentifier | CAN_EFF_FLAG) : canIdentifier;

    if (dataLengthCode <= CLASSIC_CAN_MAX_PAYLOAD) {
      NativeCanFrame classic = new NativeCanFrame();

      classic.canIdentifier = rawCanId;
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

    fd.canIdentifier = rawCanId;
    fd.length = (byte) dataLengthCode; // SocketCAN canfd_frame.len is byte length (0..64)
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

  @Override
  public void close() throws IOException {
    int result = libC.close(this.socketFileDescriptor);
    if (result != 0) {
      int errno = libC.getLastError();
      throw new IOException("close() failed errno=" + errno);
    }
  }

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

  // Package-private constructor for tests: no native calls, no probing, no drama.
  SocketCanDevice(LibCFacade libC, CanCapabilities canCapabilities, int socketFileDescriptor, String interfaceName) {
    this.libC = libC;
    this.canCapabilities = canCapabilities;
    this.socketFileDescriptor = socketFileDescriptor;
    this.interfaceName = interfaceName;
  }
}
