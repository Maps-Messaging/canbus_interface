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

import com.sun.jna.Pointer;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import io.mapsmessaging.canbus.device.frames.NativeCanFdFrame;
import io.mapsmessaging.canbus.device.frames.NativeCanFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SocketCanDeviceTest {

  @Test
  void readFrame_classic_returnsCanFrame() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    NativeCanFrame classic = new NativeCanFrame();
    classic.canIdentifier = 0x123; // standard
    classic.dataLengthCode = (byte) 3;
    classic.data[0] = 0x01;
    classic.data[1] = 0x02;
    classic.data[2] = 0x03;
    classic.write();

    when(libC.read(eq(42), any(Pointer.class), anyInt()))
        .thenAnswer(invocation -> {
          Pointer buffer = invocation.getArgument(1, Pointer.class);
          byte[] bytes = classic.getPointer().getByteArray(0, classic.size());
          buffer.write(0, bytes, 0, bytes.length);
          return classic.size();
        });

    CanFrame frame = device.readFrame();

    Assertions.assertEquals(0x123, frame.getCanIdentifier());
    Assertions.assertFalse(frame.isExtendedFrame());
    Assertions.assertEquals(3, frame.getDataLengthCode());
    Assertions.assertEquals(3, frame.getData().length);
    Assertions.assertEquals(0x01, frame.getData()[0]);
    Assertions.assertEquals(0x02, frame.getData()[1]);
    Assertions.assertEquals(0x03, frame.getData()[2]);
  }


  @Test
  void readFrame_fd_returnsCanFrame() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    NativeCanFdFrame fd = new NativeCanFdFrame();
    fd.canIdentifier = 0x456;
    fd.length = (byte) 12;
    fd.flags = 0;
    for (int i = 0; i < 12; i++) {
      fd.data[i] = (byte) (i + 1);
    }
    fd.write();

    when(libC.read(eq(42), any(Pointer.class), anyInt()))
        .thenAnswer(invocation -> {
          Pointer buffer = invocation.getArgument(1, Pointer.class);
          byte[] bytes = fd.getPointer().getByteArray(0, fd.size());
          buffer.write(0, bytes, 0, bytes.length);
          return fd.size();
        });

    CanFrame frame = device.readFrame();

    Assertions.assertEquals(0x456, frame.getCanIdentifier());
    Assertions.assertEquals(12, frame.getDataLengthCode());
    Assertions.assertEquals(12, frame.getData().length);
    Assertions.assertEquals(1, frame.getData()[0]);
    Assertions.assertEquals(12, frame.getData()[11]);
  }

  @Test
  void readFrame_throwsOnUnexpectedSize() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    when(libC.read(eq(42), any(Pointer.class), anyInt())).thenReturn(17);

    IOException ex = Assertions.assertThrows(IOException.class, device::readFrame);
    Assertions.assertTrue(ex.getMessage().contains("Unexpected read size"));
  }

  @Test
  void writeFrame_classic_writesClassicFrame_andPadsZeros() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    when(libC.write(eq(42), any(Pointer.class), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(2, Integer.class));

    byte[] data = new byte[] { 0x11, 0x22, 0x33, 0x44 };

    device.writeFrame(0x321, false, 4, data);

    Pointer writtenPointer = captureFirstWritePointer(libC);
    NativeCanFrame decoded = new NativeCanFrame(writtenPointer);
    decoded.read();

    Assertions.assertEquals(0x321, decoded.canIdentifier);
    Assertions.assertEquals(4, decoded.dataLengthCode & 0xFF);

    Assertions.assertEquals(0x11, decoded.data[0]);
    Assertions.assertEquals(0x22, decoded.data[1]);
    Assertions.assertEquals(0x33, decoded.data[2]);
    Assertions.assertEquals(0x44, decoded.data[3]);

    for (int i = 4; i < 8; i++) {
      Assertions.assertEquals(0, decoded.data[i], "padding should be zero at index " + i);
    }
  }

  @Test
  void writeFrame_fd_throwsWhenFdNotEnabled() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    byte[] data = new byte[12];

    IllegalArgumentException ex = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> device.writeFrame(0x555, false,12, data)
    );

    Assertions.assertTrue(ex.getMessage().contains("CAN FD not enabled"));
    verify(libC, never()).write(anyInt(), any(Pointer.class), anyInt());
  }

  @Test
  void writeFrame_fd_writesCanFdFrame_whenEnabled() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    when(libC.write(eq(42), any(Pointer.class), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(2, Integer.class));

    byte[] data = new byte[64];
    for (int i = 0; i < 12; i++) {
      data[i] = (byte) (i + 1);
    }

    device.writeFrame(0x777, false, 12, data);

    Pointer writtenPointer = captureFirstWritePointer(libC);
    NativeCanFdFrame decoded = new NativeCanFdFrame(writtenPointer);
    decoded.read();

    Assertions.assertEquals(0x777, decoded.canIdentifier);
    Assertions.assertEquals(12, decoded.length & 0xFF);

    Assertions.assertEquals(1, decoded.data[0]);
    Assertions.assertEquals(12, decoded.data[11]);

    for (int i = 12; i < 64; i++) {
      Assertions.assertEquals(0, decoded.data[i], "padding should be zero at index " + i);
    }
  }

  @Test
  void close_throwsIOException_whenCloseFails() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    when(libC.close(42)).thenReturn(-1);
    when(libC.getLastError()).thenReturn(9);

    IOException ex = Assertions.assertThrows(IOException.class, device::close);

    Assertions.assertNotNull(ex.getMessage());
    Assertions.assertTrue(ex.getMessage().contains("close"), ex.getMessage());

    verify(libC).close(42);
    verify(libC).getLastError();
  }

  @Test
  void readFrame_throwsIOException_whenReadFails() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    when(libC.read(eq(42), any(Pointer.class), anyInt())).thenReturn(-1);
    when(libC.getLastError()).thenReturn(11);

    IOException ex = Assertions.assertThrows(IOException.class, device::readFrame);
    Assertions.assertTrue(ex.getMessage().contains("read"), ex.getMessage());
    Assertions.assertTrue(ex.getMessage().contains("errno=11"), ex.getMessage());
  }

  @Test
  void readFrame_classic_throwsIOException_onInvalidDlcGreaterThan8() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    NativeCanFrame classic = new NativeCanFrame();
    classic.canIdentifier = 0x123;
    classic.dataLengthCode = (byte) 9;
    classic.write();

    when(libC.read(eq(42), any(Pointer.class), anyInt()))
        .thenAnswer(invocation -> {
          Pointer buffer = invocation.getArgument(1, Pointer.class);
          byte[] bytes = classic.getPointer().getByteArray(0, classic.size());
          buffer.write(0, bytes, 0, bytes.length);
          return classic.size();
        });

    IOException ex = Assertions.assertThrows(IOException.class, device::readFrame);
    Assertions.assertTrue(ex.getMessage().contains("Invalid classic DLC"), ex.getMessage());
  }

  @Test
  void readFrame_fd_throwsIOException_onInvalidLengthGreaterThan64() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    NativeCanFdFrame fd = new NativeCanFdFrame();
    fd.canIdentifier = 0x456;
    fd.length = (byte) 65;
    fd.flags = 0;
    fd.write();

    when(libC.read(eq(42), any(Pointer.class), anyInt()))
        .thenAnswer(invocation -> {
          Pointer buffer = invocation.getArgument(1, Pointer.class);
          byte[] bytes = fd.getPointer().getByteArray(0, fd.size());
          buffer.write(0, bytes, 0, bytes.length);
          return fd.size();
        });

    IOException ex = Assertions.assertThrows(IOException.class, device::readFrame);
    Assertions.assertTrue(ex.getMessage().contains("Invalid FD length"), ex.getMessage());
  }

  @Test
  void writeFrame_throwsIllegalArgumentException_whenDataIsNull() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    IllegalArgumentException ex = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> device.writeFrame(0x100,false, 1, null)
    );
    Assertions.assertTrue(ex.getMessage().contains("data must not be null"), ex.getMessage());
  }

  @Test
  void writeFrame_throwsIllegalArgumentException_whenDlcIsNegative() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    byte[] data = new byte[1];

    IllegalArgumentException ex = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> device.writeFrame(0x100,false, -1, data)
    );
    Assertions.assertTrue(ex.getMessage().contains("dataLengthCode must be >= 0"), ex.getMessage());
  }

  @Test
  void writeFrame_throwsIllegalArgumentException_whenDlcGreaterThan64() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    byte[] data = new byte[65];

    IllegalArgumentException ex = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> device.writeFrame(0x100,false, 65, data)
    );
    Assertions.assertTrue(ex.getMessage().contains("dataLengthCode must be 0..64"), ex.getMessage());
  }

  @Test
  void writeFrame_throwsIllegalArgumentException_whenDataLengthLessThanDlc() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    byte[] data = new byte[3];

    IllegalArgumentException ex = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> device.writeFrame(0x100, false, 4, data)
    );
    Assertions.assertTrue(ex.getMessage().contains("data.length"), ex.getMessage());
  }

  @Test
  void writeFrame_classic_throwsIOException_whenNativeWriteFails() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    when(libC.write(eq(42), any(Pointer.class), anyInt())).thenReturn(-1);
    when(libC.getLastError()).thenReturn(5);

    byte[] data = new byte[] { 0x01 };

    IOException ex = Assertions.assertThrows(IOException.class, () -> device.writeFrame(0x321,false, 1, data));
    Assertions.assertTrue(ex.getMessage().contains("write"), ex.getMessage());
    Assertions.assertTrue(ex.getMessage().contains("errno=5"), ex.getMessage());
  }

  @Test
  void writeFrame_classic_throwsIOException_onShortWrite() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(false, false, 8, 8),
        42,
        "can0"
    );

    int expectedSize = new NativeCanFrame().size();
    when(libC.write(eq(42), any(Pointer.class), anyInt())).thenReturn(expectedSize - 1);

    byte[] data = new byte[] { 0x01 };

    IOException ex = Assertions.assertThrows(IOException.class, () -> device.writeFrame(0x321,false, 1, data));
    Assertions.assertTrue(ex.getMessage().contains("Short write"), ex.getMessage());
  }

  @Test
  void writeFrame_fd_throwsIOException_whenNativeWriteFails() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    when(libC.write(eq(42), any(Pointer.class), anyInt())).thenReturn(-1);
    when(libC.getLastError()).thenReturn(12);

    byte[] data = new byte[12];

    IOException ex = Assertions.assertThrows(IOException.class, () -> device.writeFrame(0x777,false, 12, data));
    Assertions.assertTrue(ex.getMessage().contains("write"), ex.getMessage());
    Assertions.assertTrue(ex.getMessage().contains("errno=12"), ex.getMessage());
  }

  @Test
  void writeFrame_fd_throwsIOException_onShortWrite() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);

    SocketCanDevice device = newDevice(
        libC,
        new CanCapabilities(true, true, 64, 64),
        42,
        "can0"
    );

    int expectedSize = new NativeCanFdFrame().size();
    when(libC.write(eq(42), any(Pointer.class), anyInt())).thenReturn(expectedSize - 1);

    byte[] data = new byte[12];

    IOException ex = Assertions.assertThrows(IOException.class, () -> device.writeFrame(0x777, false,12, data));
    Assertions.assertTrue(ex.getMessage().contains("Short write"), ex.getMessage());
  }

  private static SocketCanDevice newDevice(
      LibCFacade libC,
      CanCapabilities canCapabilities,
      int socketFileDescriptor,
      String interfaceName
  ) {
    return new SocketCanDevice(libC, canCapabilities, socketFileDescriptor, interfaceName);
  }

  private static Pointer captureFirstWritePointer(LibCFacade libC) {
    return mockingDetails(libC)
        .getInvocations()
        .stream()
        .filter(invocation -> invocation.getMethod().getName().equals("write"))
        .map(invocation -> (Pointer) invocation.getArgument(1))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No write() invocation captured"));
  }
}
