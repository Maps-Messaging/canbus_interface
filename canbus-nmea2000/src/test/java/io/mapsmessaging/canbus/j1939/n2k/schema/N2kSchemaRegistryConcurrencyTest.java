/*
 *   Copyright [ 2024 - 2026 ] MapsMessaging B.V.
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
 */

package io.mapsmessaging.canbus.j1939.n2k.schema;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class N2kSchemaRegistryConcurrencyTest {

  @Test
  void concurrentFirstAccessReturnsOnePublishedSchema() throws Exception {
    N2kCompiledMessage message = N2kCompiledMessage.builder().pgn(129025).id("Position").build();
    N2kSchemaRegistry schemas = new N2kSchemaRegistry(
        new N2kCompiledRegistry(Map.of(129025, message)));
    int workerCount = 16;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(workerCount);
    try {
      List<Future<JsonObject>> results = new ArrayList<>();
      for (int i = 0; i < workerCount; i++) {
        results.add(workers.submit((Callable<JsonObject>) () -> {
          start.await();
          return schemas.getSchema(129025);
        }));
      }
      start.countDown();

      JsonObject published = results.getFirst().get(10, TimeUnit.SECONDS);
      assertEquals(129025, published.getAsJsonObject("properties")
          .getAsJsonObject("pgn").get("const").getAsInt());
      for (Future<JsonObject> result : results) {
        assertSame(published, result.get(10, TimeUnit.SECONDS));
      }
      assertSame(published, schemas.getSchemas().getFirst());
    } finally {
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
