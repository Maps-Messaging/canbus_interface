package io.mapsmessaging.canbus.device.codec.impl;


import io.mapsmessaging.canbus.device.codec.CanFrameStreamCodec;
import io.mapsmessaging.canbus.device.frames.CanFrame;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WaveshareUsbCanAStreamCodec implements CanFrameStreamCodec {

  private static final int PACKET_HEADER = 0xAA;
  private static final int PACKET_END = 0x55;

  private static final int TYPE_BASE = 0xC0;
  private static final int EXTENDED_FRAME_FLAG = 0x20;
  private static final int REMOTE_FRAME_FLAG = 0x10;
  private static final int DATA_LENGTH_MASK = 0x0F;

  private static final int STANDARD_IDENTIFIER_BYTES = 2;
  private static final int EXTENDED_IDENTIFIER_BYTES = 4;

  private static final int MAX_CLASSIC_CAN_DATA_LENGTH = 8;
  private static final int MAX_STANDARD_IDENTIFIER = 0x7FF;
  private static final int MAX_EXTENDED_IDENTIFIER = 0x1FFFFFFF;

  @Override
  public CanFrame read(InputStream inputStream) throws IOException {
    readUntilPacketHeader(inputStream);

    int frameType = readRequiredByte(inputStream);
    validateFrameType(frameType);

    boolean extendedFrame = (frameType & EXTENDED_FRAME_FLAG) != 0;
    boolean remoteFrame = (frameType & REMOTE_FRAME_FLAG) != 0;
    int dataLengthCode = frameType & DATA_LENGTH_MASK;

    if (remoteFrame) {
      throw new IOException("Waveshare remote CAN frames are not supported by CanFrame");
    }

    int canIdentifier = readCanIdentifier(inputStream, extendedFrame);

    byte[] payload = new byte[dataLengthCode];
    readFully(inputStream, payload);

    int packetEnd = readRequiredByte(inputStream);
    if (packetEnd != PACKET_END) {
      throw new IOException("Invalid Waveshare packet end byte 0x" + Integer.toHexString(packetEnd) + ", expected 0x55");
    }

    return new CanFrame(canIdentifier, extendedFrame, dataLengthCode, payload);
  }

  @Override
  public void write(OutputStream outputStream, CanFrame canFrame) throws IOException {
    if (canFrame == null) {
      throw new IllegalArgumentException("canFrame must not be null");
    }

    int canIdentifier = canFrame.canIdentifier();
    boolean extendedFrame = canFrame.extendedFrame();
    int dataLengthCode = canFrame.dataLengthCode();
    byte[] payload = canFrame.data();

    validateCanFrame(canIdentifier, extendedFrame, dataLengthCode, payload);

    int frameType = TYPE_BASE | dataLengthCode;
    if (extendedFrame) {
      frameType = frameType | EXTENDED_FRAME_FLAG;
    }

    outputStream.write(PACKET_HEADER);
    outputStream.write(frameType);
    writeCanIdentifier(outputStream, canIdentifier, extendedFrame);
    outputStream.write(payload, 0, dataLengthCode);
    outputStream.write(PACKET_END);
  }

  private static void readUntilPacketHeader(InputStream inputStream) throws IOException {
    int nextByte = readRequiredByte(inputStream);
    while (nextByte != PACKET_HEADER) {
      nextByte = readRequiredByte(inputStream);
    }
  }

  private static int readCanIdentifier(InputStream inputStream, boolean extendedFrame) throws IOException {
    int identifierByteCount = extendedFrame ? EXTENDED_IDENTIFIER_BYTES : STANDARD_IDENTIFIER_BYTES;
    int canIdentifier = 0;

    for (int index = 0; index < identifierByteCount; index++) {
      int identifierByte = readRequiredByte(inputStream);
      canIdentifier = canIdentifier | (identifierByte << (index * 8));
    }

    if (extendedFrame) {
      return canIdentifier & MAX_EXTENDED_IDENTIFIER;
    }

    return canIdentifier & MAX_STANDARD_IDENTIFIER;
  }

  private static void writeCanIdentifier(
      OutputStream outputStream,
      int canIdentifier,
      boolean extendedFrame
  ) throws IOException {
    int identifierByteCount = extendedFrame ? EXTENDED_IDENTIFIER_BYTES : STANDARD_IDENTIFIER_BYTES;

    for (int index = 0; index < identifierByteCount; index++) {
      outputStream.write((canIdentifier >> (index * 8)) & 0xFF);
    }
  }

  private static void readFully(InputStream inputStream, byte[] payload) throws IOException {
    int bytesRead = 0;

    while (bytesRead < payload.length) {
      int currentRead = inputStream.read(payload, bytesRead, payload.length - bytesRead);
      if (currentRead < 0) {
        throw new EOFException("Unexpected end of stream while reading Waveshare CAN payload");
      }
      bytesRead = bytesRead + currentRead;
    }
  }

  private static int readRequiredByte(InputStream inputStream) throws IOException {
    int value = inputStream.read();
    if (value < 0) {
      throw new EOFException("Unexpected end of stream while reading Waveshare CAN frame");
    }
    return value & 0xFF;
  }

  private static void validateFrameType(int frameType) throws IOException {
    if ((frameType & TYPE_BASE) != TYPE_BASE) {
      throw new IOException("Invalid Waveshare frame type 0x" + Integer.toHexString(frameType) + ", expected type base 0xC0");
    }

    int dataLengthCode = frameType & DATA_LENGTH_MASK;
    if (dataLengthCode > MAX_CLASSIC_CAN_DATA_LENGTH) {
      throw new IOException("Invalid Waveshare CAN DLC: " + dataLengthCode);
    }
  }

  private static void validateCanFrame(
      int canIdentifier,
      boolean extendedFrame,
      int dataLengthCode,
      byte[] payload
  ) {
    if (dataLengthCode < 0 || dataLengthCode > MAX_CLASSIC_CAN_DATA_LENGTH) {
      throw new IllegalArgumentException("Invalid classic CAN DLC: " + dataLengthCode);
    }

    if (payload == null) {
      throw new IllegalArgumentException("CAN payload must not be null");
    }

    if (payload.length < dataLengthCode) {
      throw new IllegalArgumentException("CAN payload length " + payload.length + " is less than DLC " + dataLengthCode);
    }

    if (extendedFrame) {
      if (canIdentifier < 0 || canIdentifier > MAX_EXTENDED_IDENTIFIER) {
        throw new IllegalArgumentException("Invalid extended CAN identifier: 0x" + Integer.toHexString(canIdentifier));
      }
      return;
    }

    if (canIdentifier < 0 || canIdentifier > MAX_STANDARD_IDENTIFIER) {
      throw new IllegalArgumentException("Invalid standard CAN identifier: 0x" + Integer.toHexString(canIdentifier));
    }
  }
}