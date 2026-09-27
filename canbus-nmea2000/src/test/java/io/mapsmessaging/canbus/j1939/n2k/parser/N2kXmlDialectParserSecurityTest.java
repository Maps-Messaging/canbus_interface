/*
 *   Copyright [ 2024 -  2026 ] MapsMessaging B.V.
 *
 *   Licensed under the Apache License, Version 2.0 with the Commons Clause
 */

package io.mapsmessaging.canbus.j1939.n2k.parser;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class N2kXmlDialectParserSecurityTest {

  @Test
  void rejectsDoctypeDeclarations() {
    String xml = """
        <?xml version="1.0"?>
        <!DOCTYPE PGNDefinitions [
          <!ENTITY external SYSTEM "file:///etc/passwd">
        ]>
        <PGNDefinitions>
          <PGNInfo>
            <PGN>127250</PGN>
            <Description>&external;</Description>
          </PGNInfo>
        </PGNDefinitions>
        """;

    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

    assertThrows(Exception.class, () -> N2kXmlDialectParser.parse(inputStream));
  }
}
