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

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

public final class JnaLibCFacade implements LibCFacade {

  private final LibC libC;

  public JnaLibCFacade() {
    this.libC = Native.load("c", LibC.class);
  }

  @Override
  public int socket(int domain, int type, int protocol) {
    return libC.socket(domain, type, protocol);
  }

  @Override
  public int bind(int socketFileDescriptor, Structure address, int addressLength) {
    return libC.bind(socketFileDescriptor, address, addressLength);
  }

  @Override
  public int ioctl(int fileDescriptor, int request, Pointer argumentPointer) {
    return libC.ioctl(fileDescriptor, request, argumentPointer);
  }

  @Override
  public int read(int fileDescriptor, Pointer buffer, int count) {
    return libC.read(fileDescriptor, buffer, count);
  }

  @Override
  public int write(int fileDescriptor, Pointer buffer, int count) {
    return libC.write(fileDescriptor, buffer, count);
  }

  @Override
  public int close(int fileDescriptor) {
    return libC.close(fileDescriptor);
  }

  @Override
  public int getsockopt(int sockfd, int level, int optname, IntByReference optval, IntByReference optlen) {
    return libC.getsockopt(sockfd, level, optname, optval, optlen);
  }

  @Override
  public int getLastError() {
    return Native.getLastError();
  }
}
