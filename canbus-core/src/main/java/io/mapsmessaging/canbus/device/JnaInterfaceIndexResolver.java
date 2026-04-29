/*
 *   Copyright [ 2024 -  2026 ] MapsMessaging B.V.
 *
 *   Licensed under the Apache License, Version 2.0 with the Commons Clause
 *   (the "License"); you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *       https://commonsclause.com/
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
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
