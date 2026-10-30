/*
 * Copyright [2026] MapsMessaging B.V.
 * Licensed under the Apache License, Version 2.0 with the Commons Clause.
 */
package io.mapsmessaging.canbus.j1939.n2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.stream.Stream;

/** Literal digit-pair vectors following the pinned CANboat DECIMAL metadata and print.c rules. */
class CanboatDscDecimalTest {
  private static N2kMessageParser parser;

  @BeforeAll
  static void loadCatalog() throws Exception {
    parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
  }

  @TestFactory
  Stream<DynamicTest> literalAddressesEncodeAndDecode() {
    return Stream.of(
        "112, 112, dscDistressCallInformation, 002241020, 0016290200",
        "120, 112, dscDistressAcknowledgementOrRelay, 002241020, 0016290200",
        "120, 100, dscCallInformation, 002241020, 0016290200",
        "102, 112, dscDistressAcknowledgementOrRelay, 1520040510, 0f1404050a",
        "102, 100, dscCallInformation, 1520040510, 0f1404050a",
        "102, 100, dscCallInformation, 0022410240, 0016290228",
        "102, 100, dscCallInformation, 2440608071, 18283c5047",
        "120, 100, dscCallInformation, 2440608071, 18283c5047")
        .flatMap(row -> {
          String[] values = row.split(", ");
          int format = Integer.parseInt(values[0]);
          int category = Integer.parseInt(values[1]);
          String name = values[2];
          String address = values[3];
          String hex = values[4];
          return Stream.of(
              DynamicTest.dynamicTest("decode " + row, () -> {
                JsonObject decoded = parser.decodeToJson(129808, payload(format, category, hex));
                assertEquals(name, decoded.get("name").getAsString());
                assertEquals(address, decoded.getAsJsonObject("packet")
                    .get("dscMessageAddress").getAsString());
              }),
              DynamicTest.dynamicTest("encode " + row, () -> {
                JsonObject envelope = envelope(format, name);
                envelope.getAsJsonObject("packet").addProperty("dscMessageAddress", address);
                byte[] encoded = parser.encodeFromJson(129808, envelope);
                assertArrayEquals(HexFormat.of().parseHex(hex), Arrays.copyOfRange(encoded, 2, 7));
              }));
        });
  }

  @Test
  void distressShipMmsiDoesNotInheritGeographicAddressException() {
    byte[] payload = payload(102, 112, "0f1404050a");
    byte[] mmsi = HexFormat.of().parseHex("0016290200");
    System.arraycopy(mmsi, 0, payload, 35, mmsi.length);
    JsonObject packet = parser.decodeToJson(129808, payload).getAsJsonObject("packet");
    assertEquals("1520040510", packet.get("dscMessageAddress").getAsString());
    assertEquals("002241020", packet.get("mmsiOfShipInDistress").getAsString());

    JsonObject envelope = envelope(102, "dscDistressAcknowledgementOrRelay");
    envelope.getAsJsonObject("packet").addProperty("mmsiOfShipInDistress", "002241020");
    assertArrayEquals(mmsi, Arrays.copyOfRange(parser.encodeFromJson(129808, envelope), 35, 40));
  }

  @Test
  void unavailableAddressRemainsNullAndEncodesAsAllOnes() {
    JsonObject packet = parser.decodeToJson(129808, payload(120, 100, "ffffffffff"))
        .getAsJsonObject("packet");
    assertTrue(packet.get("dscMessageAddress").isJsonNull());
    byte[] encoded = parser.encodeFromJson(129808, envelope(120, "dscCallInformation"));
    assertArrayEquals(HexFormat.of().parseHex("ffffffffff"), Arrays.copyOfRange(encoded, 2, 7));
  }

  private static JsonObject envelope(int format, String name) {
    JsonObject packet = new JsonObject();
    packet.addProperty(name.equals("dscDistressCallInformation") ? "dscFormat" : "dscFormatSymbol", format);
    JsonObject envelope = new JsonObject();
    envelope.addProperty("name", name);
    envelope.add("packet", packet);
    return envelope;
  }

  private static byte[] payload(int format, int category, String address) {
    // Complete synthetic record: empty telephone LAU at byte 21, then 12 position/time bytes.
    byte[] payload = new byte[62];
    Arrays.fill(payload, (byte) 0xff);
    payload[0] = (byte) format;
    payload[1] = (byte) category;
    System.arraycopy(HexFormat.of().parseHex(address), 0, payload, 2, 5);
    payload[21] = 2;
    payload[22] = 1;
    return payload;
  }
}
