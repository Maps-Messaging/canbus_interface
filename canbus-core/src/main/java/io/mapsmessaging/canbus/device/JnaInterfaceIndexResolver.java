/*
 *  Copyright ...
 */
package io.mapsmessaging.canbus.device;

import com.sun.jna.Pointer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.mapsmessaging.canbus.device.IfReq.IFNAMSIZ;

public final class JnaInterfaceIndexResolver implements InterfaceIndexResolver {

  private static final int SIOCGIFINDEX = 0x8933;

  private final LibCFacade libC;

  public JnaInterfaceIndexResolver(LibCFacade libC) {
    if (libC == null) {
      throw new IllegalArgumentException("libC must not be null");
    }
    this.libC = libC;
  }

  @Override
  public int resolveInterfaceIndex(int socketFileDescriptor, String interfaceName) throws IOException {
    IfReq ifRequest = new IfReq();
    Arrays.fill(ifRequest.interfaceName, (byte) 0);

    byte[] nameBytes = interfaceName.getBytes(StandardCharsets.US_ASCII);
    int copyLength = Math.min(nameBytes.length, IFNAMSIZ - 1);
    System.arraycopy(nameBytes, 0, ifRequest.interfaceName, 0, copyLength);

    ifRequest.interfaceIndex = 0;
    Arrays.fill(ifRequest.padding, (byte) 0);
    ifRequest.write();

    Pointer argumentPointer = ifRequest.getPointer();
    int ioctlResult = libC.ioctl(socketFileDescriptor, SIOCGIFINDEX, argumentPointer);
    if (ioctlResult != 0) {
      throw new IOException("ioctl(SIOCGIFINDEX," + interfaceName + ") failed errno=" + libC.getLastError());
    }

    ifRequest.read();
    if (ifRequest.interfaceIndex <= 0) {
      throw new IOException("Interface index not resolved for " + interfaceName);
    }

    return ifRequest.interfaceIndex;
  }
}
