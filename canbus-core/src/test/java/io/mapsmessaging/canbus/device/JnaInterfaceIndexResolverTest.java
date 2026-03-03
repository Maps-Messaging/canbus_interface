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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

class JnaInterfaceIndexResolverTest {

  @Test
  void resolvesIndex_whenIfNameToIndexReturnsPositive() throws Exception {
    LibCFacade libC = Mockito.mock(LibCFacade.class);

    Mockito.when(libC.ifNameToIndex("vcan0")).thenReturn(3);

    JnaInterfaceIndexResolver resolver = new JnaInterfaceIndexResolver(libC);

    int index = resolver.resolveInterfaceIndex(123, "vcan0");

    Assertions.assertEquals(3, index);
    Mockito.verify(libC).ifNameToIndex("vcan0");
  }

  @Test
  void trimsWhitespace_beforeCallingLibC() throws Exception {
    LibCFacade libC = Mockito.mock(LibCFacade.class);

    Mockito.when(libC.ifNameToIndex("vcan0")).thenReturn(7);

    JnaInterfaceIndexResolver resolver = new JnaInterfaceIndexResolver(libC);

    int index = resolver.resolveInterfaceIndex(123, " \tvcan0 \r\n");

    Assertions.assertEquals(7, index);
    Mockito.verify(libC).ifNameToIndex("vcan0");
  }

  @Test
  void throwsIllegalArgument_whenInterfaceNameNull() {
    LibCFacade libC = Mockito.mock(LibCFacade.class);
    JnaInterfaceIndexResolver resolver = new JnaInterfaceIndexResolver(libC);

    Assertions.assertThrows(IllegalArgumentException.class, () -> resolver.resolveInterfaceIndex(1, null));
    Mockito.verifyNoInteractions(libC);
  }

  @Test
  void throwsIllegalArgument_whenInterfaceNameBlank() {
    LibCFacade libC = Mockito.mock(LibCFacade.class);
    JnaInterfaceIndexResolver resolver = new JnaInterfaceIndexResolver(libC);

    Assertions.assertThrows(IllegalArgumentException.class, () -> resolver.resolveInterfaceIndex(1, "   "));
    Mockito.verifyNoInteractions(libC);
  }

  @Test
  void throwsIOException_whenIfNameToIndexReturnsZero() {
    LibCFacade libC = Mockito.mock(LibCFacade.class);

    Mockito.when(libC.ifNameToIndex("vcan0")).thenReturn(0);
    Mockito.when(libC.getLastError()).thenReturn(19);

    JnaInterfaceIndexResolver resolver = new JnaInterfaceIndexResolver(libC);

    IOException exception = Assertions.assertThrows(IOException.class, () -> resolver.resolveInterfaceIndex(99, "vcan0"));
    Assertions.assertTrue(exception.getMessage().contains("if_nametoindex(vcan0)"));
    Assertions.assertTrue(exception.getMessage().contains("errno=19"));
  }

  @Test
  void throwsIOException_whenIfNameToIndexReturnsNegative() {
    LibCFacade libC = Mockito.mock(LibCFacade.class);

    Mockito.when(libC.ifNameToIndex("vcan0")).thenReturn(-1);
    Mockito.when(libC.getLastError()).thenReturn(19);

    JnaInterfaceIndexResolver resolver = new JnaInterfaceIndexResolver(libC);

    IOException exception = Assertions.assertThrows(IOException.class, () -> resolver.resolveInterfaceIndex(99, "vcan0"));
    Assertions.assertTrue(exception.getMessage().contains("if_nametoindex(vcan0)"));
    Assertions.assertTrue(exception.getMessage().contains("errno=19"));
  }
}