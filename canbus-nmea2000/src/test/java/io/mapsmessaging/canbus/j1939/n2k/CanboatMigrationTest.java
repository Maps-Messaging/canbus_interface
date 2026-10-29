/*
 * Copyright [2026] MapsMessaging B.V.
 * Licensed under the Apache License, Version 2.0 with the Commons Clause.
 */
package io.mapsmessaging.canbus.j1939.n2k;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiler;
import io.mapsmessaging.canbus.j1939.n2k.parser.N2kXmlDialectParser;
import io.mapsmessaging.canbus.j1939.n2k.schema.N2kSchemaRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CanboatMigrationTest {
  @Test
  void default_is_complete_canboat_database() throws Exception {
    assertEquals(640, N2kXmlDialectParser.parseFromClasspath().size());
    assertEquals(89, N2kXmlDialectParser.parseFromClasspath("n2k/canboat-j1939.xml").size());
  }

  @Test
  void variants_are_selected_by_wire_discriminator() throws Exception {
    N2kMessageParser parser =
        parser(
            message("alpha", field(1, "kind", "LOOKUP", 8, "<Match>1</Match>"))
                + message("beta", field(1, "kind", "LOOKUP", 8, "<Match>2</Match>")));
    assertEquals("alpha", parser.decodeToJson(130000, new byte[] {1}).get("name").getAsString());
    assertEquals("beta", parser.decodeToJson(130000, new byte[] {2}).get("name").getAsString());
    assertThrows(IllegalArgumentException.class, () -> parser.decodeToJson(130000, new byte[] {3}));
  }

  @Test
  void repeating_fields_round_trip_and_reject_truncation() throws Exception {
    String xml =
        "<PGNInfo><PGN>130000</PGN><Id>rows</Id><Type>Fast</Type><MinLength>1</MinLength>"
            + "<RepeatingFieldSet1Size>1</RepeatingFieldSet1Size><RepeatingFieldSet1StartField>2</RepeatingFieldSet1StartField>"
            + "<RepeatingFieldSet1CountField>1</RepeatingFieldSet1CountField><Fields>"
            + field(1, "count", "NUMBER", 8, "")
            + field(2, "value", "NUMBER", 8, "")
            + "</Fields></PGNInfo>";
    N2kMessageParser parser = parser(xml);
    byte[] raw = {2, 17, 29};
    JsonObject decoded = parser.decodeToJson(130000, raw);
    assertEquals(2, decoded.getAsJsonObject("packet").getAsJsonArray("list").size());
    assertArrayEquals(raw, parser.encodeFromJson(130000, decoded));
    assertThrows(
        IllegalArgumentException.class, () -> parser.decodeToJson(130000, new byte[] {2, 17}));
  }

  @Test
  void float_is_ieee754_and_unknown_types_do_not_become_numbers() throws Exception {
    N2kMessageParser parser = parser(message("float", field(1, "value", "FLOAT", 32, "")));
    assertEquals(
        1.5,
        parser
            .decodeToJson(130000, new byte[] {0, 0, (byte) 0xc0, 0x3f})
            .getAsJsonObject("packet")
            .get("value")
            .getAsDouble());
    assertThrows(
        IllegalArgumentException.class,
        () -> parser(message("bad", field(1, "value", "MYSTERY", 8, ""))));
  }

  @Test
  void j1939_continued_spn_bits_round_trip_as_one_value() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getJ1939Parser());
    byte[] bytes = {0, 0, 0x34, 0x12, (byte) 0xa3, 7};
    JsonObject envelope = parser.decodeToJson(65226, bytes);
    JsonObject dtc =
        envelope.getAsJsonObject("packet").getAsJsonArray("list").get(0).getAsJsonObject();
    assertEquals(0x51234, dtc.get("spn").getAsInt());
    assertFalse(dtc.has("spnHigh"));
    assertArrayEquals(bytes, parser.encodeFromJson(65226, envelope));
  }

  @Test
  void signed_numeric_encoding_rejects_overflow_instead_of_wrapping() throws Exception {
    N2kMessageParser parser =
        parser(
            message(
                "signed",
                field(1, "value", "NUMBER", 8, "")
                    .replace("<Signed>false</Signed>", "<Signed>true</Signed>")));
    JsonObject packet = new JsonObject();
    packet.addProperty("value", -129);
    JsonObject envelope = new JsonObject();
    envelope.add("packet", packet);
    assertThrows(IllegalArgumentException.class, () -> parser.encodeFromJson(130000, envelope));
    packet.addProperty("value", 128);
    assertThrows(IllegalArgumentException.class, () -> parser.encodeFromJson(130000, envelope));
  }

  @Test
  void canonical_schema_includes_repeating_group_structure() throws Exception {
    JsonObject schema = new N2kSchemaRegistry(N2kParserFactory.getJ1939Parser()).getSchema(65226);
    assertNotNull(
        schema
            .getAsJsonObject("properties")
            .getAsJsonObject("packet")
            .getAsJsonObject("properties")
            .getAsJsonObject("list"));
  }

  @TestFactory
  Stream<DynamicTest> every_canonical_variant_encodes_to_a_stable_canboat_message()
      throws Exception {
    return Stream.of(N2kParserFactory.getN2kParser(), N2kParserFactory.getJ1939Parser())
        .flatMap(
            registry ->
                registry.getDatabase().getMessages().stream()
                    .filter(message -> !message.fallback())
                    .map(
                        message ->
                            DynamicTest.dynamicTest(
                                registry.getDatabase().getProtocol()
                                    + ":"
                                    + message.pgn()
                                    + ":"
                                    + message.id(),
                                () -> {
                                  N2kMessageParser parser = new N2kMessageParser(registry);
                                  JsonObject envelope = new JsonObject();
                                  envelope.addProperty("name", message.id());
                                  envelope.add("packet", new JsonObject());
                                  byte[] bytes = parser.encodeFromJson(message.pgn(), envelope);
                                  // The reserved-only Simnet Event definition overlaps the
                                  // preceding Alarm
                                  // when every byte is unavailable; CANboat resolves such overlaps
                                  // in source order.
                                  String expected =
                                      message.id().equals("simnetEvent")
                                          ? "simnetAlarm"
                                          : message.id();
                                  JsonObject decoded = parser.decodeToJson(message.pgn(), bytes);
                                  assertEquals(expected, decoded.get("name").getAsString());
                                  assertEquals(
                                      expected,
                                      parser
                                          .decodeToJson(
                                              message.pgn(),
                                              parser.encodeFromJson(message.pgn(), decoded))
                                          .get("name")
                                          .getAsString());
                                })));
  }

  @Test
  void unknown_manufacturer_for_known_pgn_uses_raw_range_fallback() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    byte[] bytes = java.util.HexFormat.of().parseHex("0100000000000000");
    JsonObject envelope = parser.decodeToJson(130945, bytes);
    assertEquals(
        "0x1ff000x1ffffManufacturerSpecificFastPacketNonAddressed",
        envelope.get("name").getAsString());
    assertArrayEquals(bytes, parser.encodeFromJson(130945, envelope));
    envelope.addProperty("name", "unknownName");
    assertThrows(IllegalArgumentException.class, () -> parser.encodeFromJson(130945, envelope));
  }

  @Test
  void named_range_fallback_round_trips_raw_bytes() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    byte[] bytes = {1, 2};
    assertArrayEquals(bytes, parser.encodeFromJson(130999, parser.decodeToJson(130999, bytes)));
  }

  @Test
  void lz_length_excludes_prefix_and_zero_terminator() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    JsonObject envelope =
        com.google.gson.JsonParser.parseString(
                "{\"name\":\"fusionDeviceName\",\"packet\":{\"name\":\"A\"}}")
            .getAsJsonObject();
    byte[] expected = java.util.HexFormat.of().parseHex("a3992180014100");
    assertArrayEquals(expected, parser.encodeFromJson(130820, envelope));
    assertEquals(
        "A",
        parser.decodeToJson(130820, expected).getAsJsonObject("packet").get("name").getAsString());
  }

  @Test
  void dynamic_length_subtracts_metadata_overhead() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    JsonObject envelope =
        parser.decodeToJson(130823, java.util.HexFormat.of().parseHex("1399ffff0501ff0400010042"));
    assertEquals(
        "Qg==",
        envelope
            .getAsJsonObject("packet")
            .getAsJsonArray("list")
            .get(0)
            .getAsJsonObject()
            .get("value")
            .getAsString());
  }

  @Test
  void text_control_one_uses_latin1_when_bytes_are_not_valid_utf8() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    JsonObject packet =
        parser
            .decodeToJson(126998, java.util.HexFormat.of().parseHex("050141ff4202010201"))
            .getAsJsonObject("packet");
    assertEquals("AÿB", packet.get("installationDescription1").getAsString());
  }

  @Test
  void dynamic_numeric_missing_and_empty_values_are_not_numbers() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    for (String hex : new String[] {"7d990b41ffffffff", "7d997e00"}) {
      JsonObject row =
          parser
              .decodeToJson(130824, java.util.HexFormat.of().parseHex(hex))
              .getAsJsonObject("packet")
              .getAsJsonArray("list")
              .get(0)
              .getAsJsonObject();
      assertTrue(row.get("value").isJsonNull());
    }
  }

  @Test
  void referenced_bit_field_discards_byte_padding() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    JsonObject row =
        parser
            .decodeToJson(126208, java.util.HexFormat.of().parseHex("0112f101ff0105fc"))
            .getAsJsonObject("packet")
            .getAsJsonArray("list")
            .get(0)
            .getAsJsonObject();
    assertEquals(0, row.get("value").getAsInt());
  }

  @Test
  void unsupported_mixed_frame_remains_observable() throws Exception {
    io.mapsmessaging.canbus.j1939.n2k.framing.FrameHandler handler =
        new io.mapsmessaging.canbus.j1939.n2k.framing.FrameHandler(
            new N2kMessageParser(N2kParserFactory.getN2kParser()));
    int identifier = io.mapsmessaging.canbus.j1939.CanIdBuilder.build(127999, 6, 42, 255);
    assertInstanceOf(
        io.mapsmessaging.canbus.j1939.n2k.framing.message.UnknownMessage.class,
        handler.onFrame(identifier, true, 3, new byte[] {1, 2, 3}).orElseThrow());
  }

  @Test
  void partial_uncounted_repeat_records_fail_instead_of_disappearing() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.decodeToJson(126464, java.util.HexFormat.of().parseHex("000102")));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.decodeToJson(130824, java.util.HexFormat.of().parseHex("7d997e")));
  }

  @Test
  void rds_g0_field_uses_its_declared_character_set() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kParserFactory.getN2kParser());
    JsonObject packet =
        parser
            .decodeToJson(
                130820,
                java.util.HexFormat.of().parseHex("a3990b800102e0516806c708426f6875736c916e00"))
            .getAsJsonObject("packet");
    assertEquals("Bohuslän", packet.get("track").getAsString());
  }

  private static N2kMessageParser parser(String messages) throws Exception {
    String xml =
        "<PGNDefinitions><SchemaVersion>2.8.0</SchemaVersion><Version>9.1.0</Version><Protocol>nmea2000</Protocol><PGNs>"
            + messages
            + "</PGNs></PGNDefinitions>";
    return new N2kMessageParser(
        N2kCompiler.compile(
            N2kXmlDialectParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))));
  }

  private static String message(String id, String fields) {
    return "<PGNInfo><PGN>130000</PGN><Id>"
        + id
        + "</Id><Type>Fast</Type><MinLength>1</MinLength><Fields>"
        + fields
        + "</Fields></PGNInfo>";
  }

  private static String field(int order, String id, String type, int bits, String extra) {
    return "<Field><Order>"
        + order
        + "</Order><Id>"
        + id
        + "</Id><FieldType>"
        + type
        + "</FieldType><BitLength>"
        + bits
        + "</BitLength>"
        + "<BitOffset>"
        + ((order - 1) * 8)
        + "</BitOffset><Signed>false</Signed><Resolution>1</Resolution>"
        + extra
        + "</Field>";
  }
}
