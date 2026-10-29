/*
 * Copyright [2026] MapsMessaging B.V.
 * Licensed under the Apache License, Version 2.0 with the Commons Clause.
 */
package io.mapsmessaging.canbus.j1939.n2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiler;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.canbus.j1939.n2k.parser.N2kXmlDialectParser;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Independent CANboat wire vectors, pinned to upstream commit
 * 5b8340403e009b935f6a398b1ef0244a03e3d128, analyzer/tests/. Expected values are derived from
 * upstream .out files and canonical XML SI units; these checks deliberately do not derive their
 * expectations by re-encoding.
 */
class CanboatGoldenVectorsTest {
  private static List<N2kMessageDefinition> nmeaDefinitions;
  private static List<N2kMessageDefinition> j1939Definitions;
  private static N2kCompiledRegistry nmeaRegistry;
  private static N2kMessageParser nmea;
  private static N2kMessageParser j1939;

  @BeforeAll
  static void loadCanonicalCatalogs() throws Exception {
    nmeaDefinitions = N2kXmlDialectParser.parseFromClasspath("n2k/canboat.xml");
    j1939Definitions = N2kXmlDialectParser.parseFromClasspath("n2k/canboat-j1939.xml");
    nmeaRegistry = N2kCompiler.compile(nmeaDefinitions);
    nmea = new N2kMessageParser(nmeaRegistry);
    j1939 = new N2kMessageParser(N2kCompiler.compile(j1939Definitions));
  }

  @Test
  void preservesCanonicalDefinitionAndPgnCounts() {
    assertEquals(640, nmeaDefinitions.size());
    assertEquals(
        348, nmeaDefinitions.stream().map(N2kMessageDefinition::getPgn).distinct().count());
    assertEquals(89, j1939Definitions.size());
    assertEquals(
        84, j1939Definitions.stream().map(N2kMessageDefinition::getPgn).distinct().count());
    assertEquals(
        nmeaDefinitions.stream().filter(d -> d.getPgn() == 126720).count(),
        nmeaRegistry.getVariants(126720).size());
  }

  @Test
  void selectsGarminVariantAndReadsIeeeFloat() {
    // pgn-float-units.in, first record; pgn-float-units-si.out.
    JsonObject result = nmea.decodeToJson(126720, bytes("e5 98 10 17 04 04 00 0b 00 b4 8d 2e 40"));
    assertEquals("garminAutopilotHeadingToSteer", result.get("name").getAsString());
    JsonObject packet = result.getAsJsonObject("packet");
    assertEquals(229, packet.get("manufacturerCode").getAsInt());
    assertEquals(2.7273988723754883, packet.get("headingToSteer").getAsDouble(), 1e-9);
  }

  @Test
  void preservesUnsigned64BitIsoNamesExactly() {
    // pgn-126983.in and pgn-126983-nv.out; NAME bytes decoded little endian.
    JsonObject packet =
        packet(
            nmea,
            126983,
            "01 02 03 00 f8 19 e8 e4 10 00 82 78 c0 04 01 3b 07 03 04 04 30 ef 34 12 21 67 32 32");
    assertEquals(
        new BigInteger("13868977989282490393"),
        packet.get("dataSourceNetworkIdName").getAsBigInteger());
    assertEquals(
        new BigInteger("2383025354739811331"),
        packet.get("acknowledgeSourceNetworkIdName").getAsBigInteger());
  }

  @Test
  void readsSequentialLauStringsWithUtf16Content() {
    // ubsan-utf16.in: ASCII first string, UTF16LE second string.
    JsonObject packet =
        packet(nmea, 126998, "07 01 68 65 6c 6c 6f 0c 00 77 00 f3 00 72 00 6c 00 64 00");
    assertEquals("hello", packet.get("installationDescription1").getAsString());
    assertEquals("wórld", packet.get("installationDescription2").getAsString());
  }

  @Test
  void readsReferencedProprietaryVariantInGroupFunctionRepeat() {
    // pgn-126208-request-126720.in and .out, first record.
    JsonObject result =
        nmea.decodeToJson(126208, bytes("00 00 ef 01 ff ff ff ff ff ff 03 01 87 00 03 04 04 23"));
    assertEquals("nmeaRequestGroupFunction", result.get("name").getAsString());
    JsonObject packet = result.getAsJsonObject("packet");
    assertEquals(126720, packet.get("pgn").getAsInt());
    JsonArray list = packet.getAsJsonArray("list");
    assertEquals(3, list.size());
    assertEquals(1, list.get(0).getAsJsonObject().get("parameter").getAsInt());
    assertEquals(135, list.get(0).getAsJsonObject().get("value").getAsInt());
    assertEquals(4, list.get(1).getAsJsonObject().get("value").getAsInt());
    assertEquals(35, list.get(2).getAsJsonObject().get("value").getAsInt());
  }

  @Test
  void skipsOverwideDynamicNumericValueWithoutLosingNextRecord() {
    // keyvalue-wide-field.in and .out: Length 13 exceeds numeric capacity.
    JsonObject packet =
        packet(
            nmea,
            130824,
            "7d 99 7e 20 cb 00 7c 20 00 05 32 21 a8 46 33 21 b5 4d 12 d1 f4 03 50 21 56 5e 51 21 95"
                + " 0a 21 6b f5 6c 21 56 2c 6d 21 56 2b ff");
    JsonArray list = packet.getAsJsonArray("list");
    assertEquals(7, list.size());
    assertEquals(2.03, list.get(0).getAsJsonObject().get("value").getAsDouble(), 1e-9);
    assertEquals(13, list.get(4).getAsJsonObject().get("length").getAsInt());
    JsonObject wide = list.get(4).getAsJsonObject();
    assertTrue(!wide.has("value") || wide.get("value").isJsonNull());
    assertEquals(1135.0, list.get(5).getAsJsonObject().get("value").getAsDouble(), 1e-9);
    assertEquals(1109.4, list.get(6).getAsJsonObject().get("value").getAsDouble(), 1e-9);
  }

  @Test
  void appliesJ1939PhysicalOffsetsAndRawSentinels() {
    // j1939-offset-sentinels.in and -si.out, three records.
    JsonObject normal = packet(j1939, 65262, "7d 50 ff ff ff ff ff ff");
    assertEquals(358.15, normal.get("engineCoolantTemp").getAsDouble(), 1e-9);
    assertEquals(313.15, normal.get("engineFuelTemp").getAsDouble(), 1e-9);
    assertNullField(normal, "engineOilTemp");
    JsonObject limits = packet(j1939, 65262, "fc fd ff ff ff ff fe ff");
    assertEquals(485.15, limits.get("engineCoolantTemp").getAsDouble(), 1e-9);
    assertNullField(limits, "engineFuelTemp");
    assertNullField(limits, "engineInterCoolerTemp");
    JsonObject absent = packet(j1939, 65262, "ff ff ff ff ff ff ff ff");
    assertNullField(absent, "engineCoolantTemp");
  }

  @Test
  void usesPositiveMaximumForSignedUnknown() {
    // pgn-float-units.in, User Datum record: deltaY/Z = signed 0x7fffffff.
    JsonObject packet =
        packet(
            nmea,
            129045,
            "96 00 00 00 ff ff ff 7f ff ff ff 7f 35 fa 0e 3c c2 b8 b2 bc 35 fa 0e 3d ff ff ff ff ff"
                + " ff ff 7f ff ff ff ff ff ff ff ff");
    assertEquals(1.5, packet.get("deltaX").getAsDouble(), 1e-9);
    assertNullField(packet, "deltaY");
    assertNullField(packet, "deltaZ");
    assertNullField(packet, "scale"); // IEEE754 NaN, not a scaled integer.
  }

  private static JsonObject packet(N2kMessageParser parser, int pgn, String hex) {
    JsonObject envelope = parser.decodeToJson(pgn, bytes(hex));
    assertNotNull(envelope, "PGN " + pgn);
    return envelope.getAsJsonObject("packet");
  }

  private static byte[] bytes(String hex) {
    return HexFormat.of().parseHex(hex.replace(" ", ""));
  }

  private static void assertNullField(JsonObject packet, String id) {
    assertTrue(packet.has(id), "Missing field " + id);
    assertTrue(packet.get(id).isJsonNull(), "Expected unavailable field " + id + " to be null");
  }
}
