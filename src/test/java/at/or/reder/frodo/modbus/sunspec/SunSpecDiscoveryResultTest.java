/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.modbus.sunspec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SunSpecModelBlock} and {@link SunSpecDiscoveryResult}.
 */
class SunSpecDiscoveryResultTest {

  // ---- SunSpecModelBlock ----

  @Test
  void testModelBlockProperties() {
    SunSpecModelBlock block = new SunSpecModelBlock(113, 40070, 60);

    assertEquals(113, block.modelId());
    assertEquals(40070, block.address());
    assertEquals(60, block.length());
  }

  @Test
  void testModelBlockDataAddress() {
    SunSpecModelBlock block = new SunSpecModelBlock(113, 40070, 60);
    // Data starts after 2-register header (ID + L)
    assertEquals(40072, block.dataAddress());
  }

  @Test
  void testModelBlockTotalRegisters() {
    SunSpecModelBlock block = new SunSpecModelBlock(113, 40070, 60);
    // Total = length + 2 (header)
    assertEquals(62, block.totalRegisters());
  }

  @Test
  void testModelBlockToString() {
    SunSpecModelBlock block = new SunSpecModelBlock(113, 40070, 60);
    String str = block.toString();
    assertTrue(str.contains("113"));
    assertTrue(str.contains("40070"));
    assertTrue(str.contains("60"));
  }

  // ---- SunSpecDiscoveryResult ----

  @Test
  void testDiscoveryResultOf() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60),
      new SunSpecModelBlock(120, 40131, 26)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);

    assertEquals(40000, result.baseAddress());
    assertEquals(3, result.models().size());
    assertNotNull(result.discoveryTime());
    assertEquals(3, result.modelCount());
  }

  @Test
  void testDiscoveryResultFindModel() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);

    Optional<SunSpecModelBlock> common = result.findModel(1);
    assertTrue(common.isPresent());
    assertEquals(1, common.get().modelId());

    Optional<SunSpecModelBlock> inverter = result.findModel(113);
    assertTrue(inverter.isPresent());
    assertEquals(113, inverter.get().modelId());

    Optional<SunSpecModelBlock> missing = result.findModel(999);
    assertFalse(missing.isPresent());
  }

  @Test
  void testDiscoveryResultFindInverterModelFloat() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);

    Optional<SunSpecModelBlock> inverter = result.findInverterModel();
    assertTrue(inverter.isPresent());
    assertEquals(113, inverter.get().modelId());
  }

  @Test
  void testDiscoveryResultFindInverterModelIntSf() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(103, 40069, 50)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);

    Optional<SunSpecModelBlock> inverter = result.findInverterModel();
    assertTrue(inverter.isPresent());
    assertEquals(103, inverter.get().modelId());
  }

  @Test
  void testDiscoveryResultFindInverterModelNone() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(120, 40069, 26)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);
    assertFalse(result.findInverterModel().isPresent());
  }

  @Test
  void testDiscoveryResultHasModel() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65),
      new SunSpecModelBlock(113, 40069, 60)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);

    assertTrue(result.hasModel(1));
    assertTrue(result.hasModel(113));
    assertFalse(result.hasModel(120));
  }

  @Test
  void testDiscoveryResultEmpty() {
    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, List.of());

    assertEquals(0, result.modelCount());
    assertFalse(result.findModel(1).isPresent());
    assertFalse(result.findInverterModel().isPresent());
  }

  @Test
  void testDiscoveryResultModelsUnmodifiable() {
    List<SunSpecModelBlock> models = List.of(
      new SunSpecModelBlock(1, 40002, 65)
    );

    SunSpecDiscoveryResult result = SunSpecDiscoveryResult.of(40000, models);

    org.junit.jupiter.api.Assertions.assertThrows(
      UnsupportedOperationException.class,
      () -> result.models().add(new SunSpecModelBlock(113, 40069, 60))
    );
  }
}
