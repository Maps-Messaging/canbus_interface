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

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/** Per-payload values used by dynamic, referenced and repeating fields. */
final class CanboatCodecState {
  final CanboatBits bits;
  final JsonObject root;
  final Map<Integer, BigInteger> raw;
  final Map<Integer, BigInteger> parameters;
  Integer referencePgn;
  Integer fieldIndex;
  Integer dynamicLength;
  Map<String, String> dynamic;

  CanboatCodecState(CanboatBits bits, JsonObject root) {
    this(bits, root, new HashMap<>(), new HashMap<>());
  }

  CanboatCodecState(CanboatBits bits, JsonObject root, Map<Integer, BigInteger> raw, Map<Integer, BigInteger> parameters) {
    this.bits = bits;
    this.root = root;
    this.raw = raw;
    this.parameters = parameters;
  }

  CanboatCodecState child() {
    CanboatCodecState child = new CanboatCodecState(bits, root, new HashMap<>(raw), parameters);
    child.referencePgn = referencePgn;
    return child;
  }
}
