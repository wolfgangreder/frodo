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

import at.or.reder.frodo.TimeUtil;
import at.or.reder.frodo.cost.entity.CostControlConfigEntity;
import at.or.reder.frodo.cost.repository.DailyCostRepository;
import at.or.reder.frodo.cost.repository.EnergyPriceRepository;
import at.or.reder.frodo.cost.repository.HourlyCostRepository;
import at.or.reder.frodo.cost.repository.HourlyEnergyRepository;
import at.or.reder.frodo.cost.repository.MonthlyCostRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Daily cleanup service for cost control time-series tables.
 *
 * <p>Runs at 03:00 every day (one hour after {@code MetricsRetentionService} at 02:00).
 * Retention periods are read from the DB-backed config on each run.</p>
 *
 * <p>Tables pruned:</p>
 * <ul>
 *   <li>{@code FroHourlyEnergy} — older than {@code retentionHourlyDays}</li>
 *   <li>{@code FroHourlyCost} — older than {@code retentionHourlyDays}</li>
 *   <li>{@code FroEnergyPrice} — older than {@code retentionHourlyDays}</li>
 *   <li>{@code FroDailyCost} — older than {@code retentionMonthlyYears}</li>
 *   <li>{@code FroMonthlyCost} — older than {@code retentionMonthlyYears}</li>
 * </ul>
 *
 * <p>Reference tables ({@code FroFixedCost}, {@code FroGridFee}, {@code FroTariffWindow})
 * are never auto-pruned — they contain infrequent manual configuration data.</p>
 */
@ApplicationScoped
public class CostRetentionService {

  private static final Logger LOG = Logger.getLogger(CostRetentionService.class);
  private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Inject
  CostControlConfigService configService;

  @Inject
  HourlyEnergyRepository hourlyEnergyRepository;

  @Inject
  HourlyCostRepository hourlyCostRepository;

  @Inject
  EnergyPriceRepository energyPriceRepository;

  @Inject
  DailyCostRepository dailyCostRepository;

  @Inject
  MonthlyCostRepository monthlyCostRepository;

  /**
   * Runs daily at 03:00 and prunes expired records.
   */
  @Scheduled(cron = "0 0 3 * * ?", identity = "cost-retention")
  @Transactional
  void prune() {
    CostControlConfigEntity cfg;
    try {
      cfg = configService.load();
    } catch (Exception ex) {
      LOG.warnf("Cost retention skipped: cannot load config (%s)", ex.getMessage());
      return;
    }

    LocalDateTime hourlyCutoff = TimeUtil.nowUtc()
      .minusDays(cfg.retentionHourlyDays);

    int deletedEnergy = hourlyEnergyRepository.deleteOlderThan(hourlyCutoff);
    int deletedCost = hourlyCostRepository.deleteOlderThan(hourlyCutoff);
    int deletedPrices = energyPriceRepository.deleteExpired(hourlyCutoff);

    // Monthly and daily: delete if key < cutoff
    String monthlyCutoff = TimeUtil.todayUtc()
      .minusYears(cfg.retentionMonthlyYears)
      .format(YEAR_MONTH_FMT);
    String dailyCutoff = TimeUtil.todayUtc()
      .minusYears(cfg.retentionMonthlyYears)
      .format(DAY_FMT);
    int deletedDaily = dailyCostRepository.deleteOlderThan(dailyCutoff);
    int deletedMonthly = monthlyCostRepository.deleteOlderThan(monthlyCutoff);

    LOG.infof(
      "Cost retention: deleted %d energy rows, %d cost rows, %d price rows, %d daily rows, %d monthly rows",
      deletedEnergy, deletedCost, deletedPrices, deletedDaily, deletedMonthly);
  }
}
