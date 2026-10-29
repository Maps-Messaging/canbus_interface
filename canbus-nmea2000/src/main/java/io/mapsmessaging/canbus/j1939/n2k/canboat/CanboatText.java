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

package io.mapsmessaging.canbus.j1939.n2k.canboat;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * CANboat text encodings; RDS table from CANboat keel/src/charset.rs, Apache-2.0, (C) 2009-2026
 * Kees Verruijt. IEC62106 G0, not the later DAB character repertoire.
 */
final class CanboatText {
  private CanboatText() {}

  private static final int[] RDS_G0 = {
    0x0020, 0x0021, 0x0022, 0x0023, 0x00A4, 0x0025, 0x0026, 0x0027, 0x0028, 0x0029, 0x002A, 0x002B,
    0x002C, 0x002D, 0x002E, 0x002F, 0x0030, 0x0031, 0x0032, 0x0033, 0x0034, 0x0035, 0x0036, 0x0037,
    0x0038, 0x0039, 0x003A, 0x003B, 0x003C, 0x003D, 0x003E, 0x003F, 0x0040, 0x0041, 0x0042, 0x0043,
    0x0044, 0x0045, 0x0046, 0x0047, 0x0048, 0x0049, 0x004A, 0x004B, 0x004C, 0x004D, 0x004E, 0x004F,
    0x0050, 0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057, 0x0058, 0x0059, 0x005A, 0x005B,
    0x005C, 0x005D, 0x2015, 0x005F, 0x2551, 0x0061, 0x0062, 0x0063, 0x0064, 0x0065, 0x0066, 0x0067,
    0x0068, 0x0069, 0x006A, 0x006B, 0x006C, 0x006D, 0x006E, 0x006F, 0x0070, 0x0071, 0x0072, 0x0073,
    0x0074, 0x0075, 0x0076, 0x0077, 0x0078, 0x0079, 0x007A, 0x007B, 0x007C, 0x007D, 0x00AF, 0x0000,
    0x00E1, 0x00E0, 0x00E9, 0x00E8, 0x00ED, 0x00EC, 0x00F3, 0x00F2, 0x00FA, 0x00F9, 0x00D1, 0x00C7,
    0x015E, 0x00DF, 0x00A1, 0x0132, 0x00E2, 0x00E4, 0x00EA, 0x00EB, 0x00EE, 0x00EF, 0x00F4, 0x00F6,
    0x00FB, 0x00FC, 0x00F1, 0x00E7, 0x015F, 0x011F, 0x0131, 0x0133, 0x00AA, 0x03B1, 0x00A9, 0x2030,
    0x011E, 0x011B, 0x0148, 0x0151, 0x03C0, 0x20AC, 0x00A3, 0x0024, 0x2190, 0x2191, 0x2192, 0x2193,
    0x00BA, 0x00B9, 0x00B2, 0x00B3, 0x00B1, 0x0130, 0x0144, 0x0171, 0x00B5, 0x00BF, 0x00F7, 0x00B0,
    0x00BC, 0x00BD, 0x00BE, 0x00A7, 0x00C1, 0x00C0, 0x00C9, 0x00C8, 0x00CD, 0x00CC, 0x00D3, 0x00D2,
    0x00DA, 0x00D9, 0x0158, 0x010C, 0x0160, 0x017D, 0x00D0, 0x013F, 0x00C2, 0x00C4, 0x00CA, 0x00CB,
    0x00CE, 0x00CF, 0x00D4, 0x00D6, 0x00DB, 0x00DC, 0x0159, 0x010D, 0x0161, 0x017E, 0x0111, 0x0140,
    0x00C3, 0x00C5, 0x00C6, 0x0152, 0x0177, 0x00DD, 0x00D5, 0x00D8, 0x00DE, 0x014A, 0x0154, 0x0106,
    0x015A, 0x0179, 0x0166, 0x00F0, 0x00E3, 0x00E5, 0x00E6, 0x0153, 0x0175, 0x00FD, 0x00F5, 0x00F8,
    0x00FE, 0x014B, 0x0155, 0x0107, 0x015B, 0x017A, 0x0167, 0x0000,
  };

  static String decode(byte[] bytes, int control, String encoding) {
    if ("RDS_G0".equals(encoding)) {
      StringBuilder text = new StringBuilder();
      for (byte value : bytes) {
        int code = value & 255;
        int mapped = code < 0x20 ? 0 : RDS_G0[code - 0x20];
        text.append((char) (mapped == 0 ? code : mapped));
      }
      return text.toString();
    }
    try {
      return (control == 0 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8)
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException exception) {
      if (control == 0) {
        throw new IllegalArgumentException("Invalid UTF16 text", exception);
      }
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  }

  static byte[] encode(String text, String encoding) {
    if (!"RDS_G0".equals(encoding)) {
      return text.getBytes(StandardCharsets.UTF_8);
    }
    byte[] bytes = new byte[text.length()];
    for (int index = 0; index < text.length(); index++) {
      char value = text.charAt(index);
      int code = -1;
      if (value < 0x20) {
        code = value;
      } else {
        for (int candidate = 0; candidate < RDS_G0.length; candidate++) {
          if (RDS_G0[candidate] == value || RDS_G0[candidate] == 0 && candidate + 0x20 == value) {
            code = candidate + 0x20;
            break;
          }
        }
      }
      if (code < 0) {
        throw new IllegalArgumentException("Character cannot be encoded in RDS G0: " + value);
      }
      bytes[index] = (byte) code;
    }
    return bytes;
  }
}
