/*
 *  Copyright ...
 */
package io.mapsmessaging.canbus.device;

import java.io.IOException;

public interface InterfaceIndexResolver {
  int resolveInterfaceIndex(int socketFileDescriptor, String interfaceName) throws IOException;
}
