/*
 *  Copyright ...
 */
package io.mapsmessaging.canbus.device;


import java.io.IOException;

public final class JnaInterfaceIndexResolver implements InterfaceIndexResolver {

  private final LibCFacade libC;

  public JnaInterfaceIndexResolver(LibCFacade libC) {
    if (libC == null) {
      throw new IllegalArgumentException("libC must not be null");
    }
    this.libC = libC;
  }

  @Override
  public int resolveInterfaceIndex(int socketFileDescriptor, String interfaceName) throws IOException {
    if (interfaceName == null) {
      throw new IllegalArgumentException("interfaceName must not be null");
    }

    String sanitized = interfaceName.trim();
    if (sanitized.isEmpty()) {
      throw new IllegalArgumentException("interfaceName must not be empty");
    }

    int index = libC.ifNameToIndex(sanitized);
    if (index <= 0) {
      throw new IOException("if_nametoindex(" + sanitized + ") failed errno=" + libC.getLastError());
    }

    return index;
  }
}
