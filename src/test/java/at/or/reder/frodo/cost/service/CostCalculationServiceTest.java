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

package at.or.reder.frodo.cost.service;

import at.or.reder.frodo.cost.entity.GridFeeEntity;
import at.or.reder.frodo.cost.spi.FeeAppliesTo;
import at.or.reder.frodo.cost.spi.FeeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CostCalculationService#applyFees}.
 *
 * <p>All tests verify the formula:
 * {@code (market_price - grid_fees) × energy = money per hour}
 * with import and export calculated independently.</p>
 */
class CostCalculationServiceTest {

  // ---- helpers -----------------------------------------------------------

  private static GridFeeEntity fee(FeeType type, FeeAppliesTo appliesTo, String value) {
    GridFeeEntity f = new GridFeeEntity();
    f.feeType = type;
    f.appliesTo = appliesTo;
    f.feeValue = new BigDecimal(value);
    return f;
  }

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  // ---- no fees -----------------------------------------------------------

  @Test
  void noFees_pricesPassThrough() {
    CostCalculationService.EffectivePrices ep =
      CostCalculationService.applyFees(bd("20.0"), bd("8.0"), List.of());

    assertEquals(0, bd("20.0").compareTo(ep.importPriceCt()));
    assertEquals(0, bd("8.0").compareTo(ep.exportPriceCt()));
    assertEquals(0, BigDecimal.ZERO.compareTo(ep.timeFeeEur()));
  }

  // ---- ABSOLUTE_ENERGY fees ----------------------------------------------

  @Test
  void absoluteEnergy_import_subtractedFromImportOnly() {
    // 2 ct/kWh fee on IMPORT: effectiveImport = 20 - 2 = 18; export unchanged
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.IMPORT, "2.0")));

    assertEquals(0, bd("18.0").compareTo(ep.importPriceCt()), "import price");
    assertEquals(0, bd("8.0").compareTo(ep.exportPriceCt()), "export unchanged");
    assertEquals(0, BigDecimal.ZERO.compareTo(ep.timeFeeEur()), "no time fee");
  }

  @Test
  void absoluteEnergy_export_subtractedFromExportOnly() {
    // 3 ct/kWh fee on EXPORT: import unchanged; effectiveExport = 8 - 3 = 5
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.EXPORT, "3.0")));

    assertEquals(0, bd("20.0").compareTo(ep.importPriceCt()), "import unchanged");
    assertEquals(0, bd("5.0").compareTo(ep.exportPriceCt()), "export price");
  }

  @Test
  void absoluteEnergy_both_subtractedFromBothDirections() {
    // 2 ct/kWh on BOTH: import 20-2=18, export 8-2=6
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.BOTH, "2.0")));

    assertEquals(0, bd("18.0").compareTo(ep.importPriceCt()));
    assertEquals(0, bd("6.0").compareTo(ep.exportPriceCt()));
  }

  @Test
  void absoluteEnergy_multipleFeesStack() {
    // 2 ct/kWh IMPORT + 1 ct/kWh BOTH: import 20-2-1=17, export 8-1=7
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"), List.of(
      fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.IMPORT, "2.0"),
      fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.BOTH, "1.0")));

    assertEquals(0, bd("17.0").compareTo(ep.importPriceCt()));
    assertEquals(0, bd("7.0").compareTo(ep.exportPriceCt()));
  }

  @Test
  void absoluteEnergy_feeExceedsMarketPrice_resultNegative() {
    // Fees can drive price below zero; clamping is the caller's responsibility
    var ep = CostCalculationService.applyFees(bd("3.0"), bd("8.0"),
      List.of(fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.IMPORT, "5.0")));

    assertTrue(ep.importPriceCt().compareTo(BigDecimal.ZERO) < 0,
      "negative price allowed; caller clamps with max(0, ...)");
  }

  // ---- PERCENT fees ------------------------------------------------------

  @Test
  void percent_import_subtractsPercentOfRawPrice() {
    // 10% of raw import 20 ct = 2 ct deducted: effective = 18
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.PERCENT, FeeAppliesTo.IMPORT, "10.0")));

    // 20 - 20*10/100 = 20 - 2 = 18
    assertEquals(0, bd("18.0").compareTo(ep.importPriceCt()), "10% of 20 = 2 deducted");
    assertEquals(0, bd("8.0").compareTo(ep.exportPriceCt()), "export unchanged");
  }

  @Test
  void percent_export_subtractsPercentOfRawExportPrice() {
    // 25% of raw export 8 ct = 2 ct deducted: effective = 6
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.PERCENT, FeeAppliesTo.EXPORT, "25.0")));

    assertEquals(0, bd("20.0").compareTo(ep.importPriceCt()), "import unchanged");
    // 8 - 8*25/100 = 8 - 2 = 6
    assertEquals(0, bd("6.0").compareTo(ep.exportPriceCt()), "25% of 8 = 2 deducted");
  }

  @Test
  void percent_both_appliesSeparatelyPerDirection() {
    // 10% on BOTH: import 20 - 2 = 18; export 8 - 0.8 = 7.2
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.PERCENT, FeeAppliesTo.BOTH, "10.0")));

    assertEquals(0, bd("18.0").compareTo(ep.importPriceCt()));
    // 8 * 0.1 = 0.8 → 8 - 0.8 = 7.2  (scale 6 internal, but result is 7.2)
    assertEquals(0, new BigDecimal("7.2").compareTo(ep.exportPriceCt().stripTrailingZeros()));
  }

  @Test
  void percent_basedOnRawPrice_notCumulative() {
    // Two PERCENT fees on IMPORT (10% + 10%).
    // Each computed against the raw price (20), not cascaded.
    // Total deduction = 2 + 2 = 4; effective = 16.
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"), List.of(
      fee(FeeType.PERCENT, FeeAppliesTo.IMPORT, "10.0"),
      fee(FeeType.PERCENT, FeeAppliesTo.IMPORT, "10.0")));

    // 20 - (20*0.10) - (20*0.10) = 20 - 2 - 2 = 16
    assertEquals(0, bd("16.0").compareTo(ep.importPriceCt()));
  }

  // ---- ABSOLUTE_TIME fees ------------------------------------------------

  @Test
  void absoluteTime_amortizedPerHour_doesNotAffectPrices() {
    // EUR/month ÷ 730 = EUR/hour; prices unchanged
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"),
      List.of(fee(FeeType.ABSOLUTE_TIME, FeeAppliesTo.BOTH, "730.0")));

    // 730 EUR/month ÷ 730 = 1.0000 EUR/hour deducted
    assertEquals(0, bd("20.0").compareTo(ep.importPriceCt()), "import price unchanged");
    assertEquals(0, bd("8.0").compareTo(ep.exportPriceCt()), "export price unchanged");
    assertEquals(0, BigDecimal.ONE.negate().compareTo(ep.timeFeeEur().stripTrailingZeros()),
      "-1 EUR/hour time fee (deducted)");
  }

  @Test
  void absoluteTime_multipleFeesAccumulate() {
    // Two time fees: 365 + 365 EUR/month = 1 EUR/hour total
    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"), List.of(
      fee(FeeType.ABSOLUTE_TIME, FeeAppliesTo.IMPORT, "365.0"),
      fee(FeeType.ABSOLUTE_TIME, FeeAppliesTo.EXPORT, "365.0")));

    // (365 + 365) / 730 = 730/730 = 1 EUR/hour deducted
    assertEquals(0, BigDecimal.ONE.negate().compareTo(ep.timeFeeEur().stripTrailingZeros()));
    assertEquals(0, bd("20.0").compareTo(ep.importPriceCt()), "price unchanged");
  }

  // ---- mixed fees --------------------------------------------------------

  @Test
  void mixed_absoluteEnergy_and_percent_and_time() {
    // import raw = 20 ct/kWh; export raw = 10 ct/kWh
    // Fees:
    //   ABSOLUTE_ENERGY IMPORT 2 ct/kWh   → import -= 2
    //   PERCENT EXPORT 10%                → export -= 10*0.10 = 1
    //   ABSOLUTE_TIME BOTH 730 EUR/month  → timeFee = 1 EUR/h
    // Expected: importCt = 18, exportCt = 9, timeFee = 1

    var ep = CostCalculationService.applyFees(bd("20.0"), bd("10.0"), List.of(
      fee(FeeType.ABSOLUTE_ENERGY, FeeAppliesTo.IMPORT, "2.0"),
      fee(FeeType.PERCENT, FeeAppliesTo.EXPORT, "10.0"),
      fee(FeeType.ABSOLUTE_TIME, FeeAppliesTo.BOTH, "730.0")));

    assertEquals(0, bd("18.0").compareTo(ep.importPriceCt()), "effective import price");
    assertEquals(0, bd("9.0").compareTo(ep.exportPriceCt()), "effective export price");
    assertEquals(0, BigDecimal.ONE.negate().compareTo(ep.timeFeeEur().stripTrailingZeros()), "time fee");
  }

  // ---- net cost formula --------------------------------------------------

  @Test
  void importAndExportAreIndependent_noSaldo() {
    // Verify the caller formula:
    //   importCostEur   = max(0, effectiveImportPriceCt) × importKwh  / 100
    //   exportIncomeEur = max(0, effectiveExportPriceCt) × exportKwh / 100
    //   netCostEur      = importCostEur + timeFeeEur   (NOT import - export)
    //
    // importKwh=0, exportKwh=2, import price=20, export price=8, no fees
    // → importCostEur=0, exportIncomeEur=0.16, netCostEur=0 (no saldo → NOT negative)

    var ep = CostCalculationService.applyFees(bd("20.0"), bd("8.0"), List.of());

    BigDecimal importKwh = BigDecimal.ZERO;
    BigDecimal exportKwh = bd("2.0");
    BigDecimal hundred = new BigDecimal("100");

    BigDecimal importCostEur = ep.importPriceCt().max(BigDecimal.ZERO)
      .multiply(importKwh).divide(hundred, 4, java.math.RoundingMode.HALF_UP);
    BigDecimal exportIncomeEur = ep.exportPriceCt().max(BigDecimal.ZERO)
      .multiply(exportKwh).divide(hundred, 4, java.math.RoundingMode.HALF_UP);
    BigDecimal netCostEur = importCostEur.add(ep.timeFeeEur());

    assertEquals(0, BigDecimal.ZERO.compareTo(importCostEur), "no import → zero import cost");
    assertEquals(0, bd("0.1600").compareTo(exportIncomeEur), "export income = 8 ct * 2 kWh / 100");
    assertTrue(netCostEur.compareTo(BigDecimal.ZERO) >= 0,
      "netCostEur must never be negative when only export happens");
  }
}
