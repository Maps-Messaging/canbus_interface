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

import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageDefinition;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/** Retains schema metadata while preserving the existing parser's list API. */
public final class CanboatDefinitions extends AbstractList<N2kMessageDefinition>
    implements RandomAccess {
  private final List<N2kMessageDefinition> definitions;
  private final CanboatDatabase database;

  public CanboatDefinitions(List<N2kMessageDefinition> definitions, CanboatDatabase database) {
    this.definitions = List.copyOf(definitions);
    this.database = database;
  }

  public CanboatDatabase getDatabase() {
    return database;
  }

  @Override
  public N2kMessageDefinition get(int index) {
    return definitions.get(index);
  }

  @Override
  public int size() {
    return definitions.size();
  }
}
