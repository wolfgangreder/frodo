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

package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.CostControlConfigRequest;
import at.or.reder.frodo.api.dto.CostControlConfigResponse;
import at.or.reder.frodo.api.dto.EnergyPriceResponse;
import at.or.reder.frodo.api.dto.FixedCostRequest;
import at.or.reder.frodo.api.dto.FixedCostResponse;
import at.or.reder.frodo.api.dto.GridFeeRequest;
import at.or.reder.frodo.api.dto.GridFeeResponse;
import at.or.reder.frodo.api.dto.HourlyCostResponse;
import at.or.reder.frodo.api.dto.ManualPriceRequest;
import at.or.reder.frodo.api.dto.MonthlyCostResponse;
import at.or.reder.frodo.api.dto.ProviderInfoResponse;
import at.or.reder.frodo.api.dto.TariffWindowRequest;
import at.or.reder.frodo.api.dto.TariffWindowResponse;
import at.or.reder.frodo.cost.entity.CostControlConfigEntity;
import at.or.reder.frodo.cost.entity.EnergyPriceEntity;
import at.or.reder.frodo.cost.entity.FixedCostEntity;
import at.or.reder.frodo.cost.entity.GridFeeEntity;
import at.or.reder.frodo.cost.entity.HourlyCostEntity;
import at.or.reder.frodo.cost.entity.MonthlyCostEntity;
import at.or.reder.frodo.cost.entity.TariffWindowEntity;
import at.or.reder.frodo.cost.repository.EnergyPriceRepository;
import at.or.reder.frodo.cost.repository.FixedCostRepository;
import at.or.reder.frodo.cost.repository.GridFeeRepository;
import at.or.reder.frodo.cost.repository.HourlyCostRepository;
import at.or.reder.frodo.cost.repository.MonthlyCostRepository;
import at.or.reder.frodo.cost.repository.TariffWindowRepository;
import at.or.reder.frodo.cost.service.CostControlConfigService;
import at.or.reder.frodo.cost.service.EnergyPriceSchedulerService;
import at.or.reder.frodo.cost.spi.FeeAppliesTo;
import at.or.reder.frodo.cost.spi.FeeType;
import at.or.reder.frodo.cost.spi.PriceDirection;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * REST resource for cost control: configuration, tariff windows, grid fees,
 * fixed costs, hourly energy prices, and hourly/monthly cost data.
 *
 * <p>All paths are relative to {@code /api/cost-control}.</p>
 */
@Path("/cost-control")
@Tag(name = "Cost Control", description = "Energy cost tracking and configuration")
public class CostControlResource {

  private static final Logger LOG = Logger.getLogger(CostControlResource.class);

  @Inject
  CostControlConfigService configService;

  @Inject
  EnergyPriceSchedulerService priceSchedulerService;

  @Inject
  EnergyPriceRepository energyPriceRepository;

  @Inject
  HourlyCostRepository hourlyCostRepository;

  @Inject
  MonthlyCostRepository monthlyCostRepository;

  @Inject
  TariffWindowRepository tariffWindowRepository;

  @Inject
  GridFeeRepository gridFeeRepository;

  @Inject
  FixedCostRepository fixedCostRepository;

  // ---- Config ------------------------------------------------------------

  /**
   * Returns the current cost control configuration.
   *
   * @return config response
   */
  @GET
  @Path("/config")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get cost control configuration")
  public CostControlConfigResponse getConfig() {
    return toConfigResponse(configService.load());
  }

  /**
   * Updates cost control configuration. Changes take effect on the next scheduled run.
   *
   * @param request new configuration values
   * @return updated config
   */
  @PUT
  @Path("/config")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Update cost control configuration")
  @Transactional
  public CostControlConfigResponse updateConfig(CostControlConfigRequest request) {
    CostControlConfigEntity entity = configService.load();
    entity.importProviderId = request.importProviderId();
    entity.exportProviderId = request.exportProviderId();
    entity.importFetchCron = request.importFetchCron();
    entity.exportFetchCron = request.exportFetchCron();
    entity.sampleIntervalSeconds = request.sampleIntervalSeconds();
    entity.deadBandWatts = request.deadBandWatts();
    entity.retentionHourlyDays = request.retentionHourlyDays();
    entity.retentionMonthlyYears = request.retentionMonthlyYears();
    return toConfigResponse(configService.save(entity));
  }

  // ---- Providers ---------------------------------------------------------

  /**
   * Lists all registered energy price providers.
   *
   * @return provider list
   */
  @GET
  @Path("/providers")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List registered energy price providers")
  public List<ProviderInfoResponse> listProviders() {
    return priceSchedulerService.listProviders().stream()
      .map(p -> new ProviderInfoResponse(
        p.getProviderId(),
        p.getDisplayName(),
        p.isAutoFetchSupported(),
        p.getSupportedDirections().stream().map(Enum::name).toList()
      ))
      .toList();
  }

  // ---- Energy prices -----------------------------------------------------

  /**
   * Returns recent hourly energy prices (up to 48 entries, newest first).
   *
   * @param limit max entries (1–48, default 24)
   * @return price list
   */
  @GET
  @Path("/prices")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get recent hourly energy prices")
  public List<EnergyPriceResponse> getRecentPrices(
      @QueryParam("limit") Integer limit) {
    int n = (limit == null) ? 24 : Math.max(1, Math.min(limit, 48));
    return energyPriceRepository.listRecent(n).stream()
      .map(this::toPriceResponse)
      .toList();
  }

  /**
   * Triggers an immediate price fetch for the given direction.
   *
   * @param direction IMPORT or EXPORT
   */
  @POST
  @Path("/prices/refresh/{direction}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Force-refresh energy prices for a direction")
  public Response refreshPrices(@PathParam("direction") String direction) {
    PriceDirection dir = parsePriceDirection(direction);
    priceSchedulerService.refreshNow(dir);
    return Response.noContent().build();
  }

  /**
   * Sets the manual import price for a specific hour.
   *
   * @param request hour start and price in ct/kWh
   * @return the upserted price row
   */
  @PUT
  @Path("/prices/import")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Set manual import price for an hour")
  @Transactional
  public EnergyPriceResponse setImportPrice(ManualPriceRequest request) {
    LocalDateTime start = parseDateTime(request.hourStart());
    LocalDateTime end = start.plusHours(1);
    return toPriceResponse(
      energyPriceRepository.upsertImport(start, end, request.priceCt(), "MANUAL"));
  }

  /**
   * Sets the manual export price for a specific hour.
   *
   * @param request hour start and price in ct/kWh
   * @return the upserted price row
   */
  @PUT
  @Path("/prices/export")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Set manual export price for an hour")
  @Transactional
  public EnergyPriceResponse setExportPrice(ManualPriceRequest request) {
    LocalDateTime start = parseDateTime(request.hourStart());
    LocalDateTime end = start.plusHours(1);
    return toPriceResponse(
      energyPriceRepository.upsertExport(start, end, request.priceCt(), "MANUAL"));
  }

  // ---- Hourly cost -------------------------------------------------------

  /**
   * Returns hourly cost records within a date range (UTC).
   *
   * <p>Both {@code from} and {@code to} are ISO 8601 local date-time strings
   * (e.g. {@code 2026-05-01T00:00:00}). Max 744 hours returned.</p>
   *
   * @param from range start (inclusive)
   * @param to   range end (exclusive)
   * @return list of hourly cost records
   */
  @GET
  @Path("/hourly")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get hourly cost records in date range")
  public List<HourlyCostResponse> getHourlyCosts(
      @QueryParam("from") String from,
      @QueryParam("to") String to) {
    LocalDateTime dtFrom = (from == null) ? LocalDateTime.now().minusDays(7) : parseDateTime(from);
    LocalDateTime dtTo = (to == null) ? LocalDateTime.now() : parseDateTime(to);
    return hourlyCostRepository.findByDateRange(dtFrom, dtTo).stream()
      .map(this::toHourlyCostResponse)
      .toList();
  }

  /**
   * Returns the latest completed hourly cost record.
   *
   * @return latest record, or 404 if none
   */
  @GET
  @Path("/hourly/latest")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get the latest hourly cost record")
  public HourlyCostResponse getLatestHourlyCost() {
    return hourlyCostRepository.findLatest()
      .map(this::toHourlyCostResponse)
      .orElseThrow(NotFoundException::new);
  }

  // ---- Monthly cost ------------------------------------------------------

  /**
   * Returns all monthly cost summaries, newest first.
   *
   * @return list of monthly summaries
   */
  @GET
  @Path("/monthly")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get all monthly cost summaries")
  public List<MonthlyCostResponse> getMonthlyCosts() {
    return monthlyCostRepository.listAllDesc().stream()
      .map(this::toMonthlyCostResponse)
      .toList();
  }

  /**
   * Returns the monthly cost summary for a specific year-month.
   *
   * @param yearMonth year-month in format {@code yyyy-MM}
   * @return monthly summary, or 404 if not found
   */
  @GET
  @Path("/monthly/{yearMonth}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get monthly cost summary for a specific month")
  public MonthlyCostResponse getMonthlyCost(@PathParam("yearMonth") String yearMonth) {
    return monthlyCostRepository.findByYearMonth(yearMonth)
      .map(this::toMonthlyCostResponse)
      .orElseThrow(NotFoundException::new);
  }

  // ---- Tariff windows ----------------------------------------------------

  /**
   * Lists all tariff windows, optionally filtered by direction.
   *
   * @param direction optional filter: IMPORT or EXPORT
   * @return tariff window list
   */
  @GET
  @Path("/tariff-windows")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List tariff windows")
  public List<TariffWindowResponse> listTariffWindows(
      @QueryParam("direction") String direction) {
    if (direction != null && !direction.isBlank()) {
      PriceDirection dir = parsePriceDirection(direction);
      return tariffWindowRepository.listByDirection(dir).stream()
        .map(this::toTariffWindowResponse)
        .toList();
    }
    return tariffWindowRepository.listAll().stream()
      .map(this::toTariffWindowResponse)
      .toList();
  }

  /**
   * Creates a new tariff window.
   *
   * @param request tariff window data
   * @return created tariff window
   */
  @POST
  @Path("/tariff-windows")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create a tariff window")
  @Transactional
  public Response createTariffWindow(TariffWindowRequest request) {
    TariffWindowEntity entity = toTariffWindowEntity(request);
    TariffWindowEntity saved = tariffWindowRepository.save(entity);
    return Response.status(Response.Status.CREATED)
      .entity(toTariffWindowResponse(saved))
      .build();
  }

  /**
   * Updates an existing tariff window.
   *
   * @param id      database ID
   * @param request updated values
   * @return updated window, or 404 if not found
   */
  @PUT
  @Path("/tariff-windows/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Update a tariff window")
  @Transactional
  public TariffWindowResponse updateTariffWindow(
      @PathParam("id") long id, TariffWindowRequest request) {
    TariffWindowEntity update = toTariffWindowEntity(request);
    return tariffWindowRepository.update(id, update)
      .map(this::toTariffWindowResponse)
      .orElseThrow(NotFoundException::new);
  }

  /**
   * Deletes a tariff window.
   *
   * @param id database ID
   */
  @DELETE
  @Path("/tariff-windows/{id}")
  @Operation(summary = "Delete a tariff window")
  @Transactional
  public Response deleteTariffWindow(@PathParam("id") long id) {
    TariffWindowEntity entity = tariffWindowRepository.findById(id);
    if (entity == null) {
      throw new NotFoundException();
    }
    tariffWindowRepository.delete(entity);
    return Response.noContent().build();
  }

  // ---- Grid fees ---------------------------------------------------------

  /**
   * Lists all grid fees ordered by validFrom ascending.
   *
   * @return grid fee list
   */
  @GET
  @Path("/grid-fees")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List all grid fees")
  public List<GridFeeResponse> listGridFees() {
    return gridFeeRepository.listAll().stream()
      .map(this::toGridFeeResponse)
      .toList();
  }

  /**
   * Creates a new grid fee.
   *
   * @param request fee data
   * @return created fee
   */
  @POST
  @Path("/grid-fees")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create a grid fee")
  @Transactional
  public Response createGridFee(GridFeeRequest request) {
    GridFeeEntity entity = toGridFeeEntity(request);
    GridFeeEntity saved = gridFeeRepository.save(entity);
    return Response.status(Response.Status.CREATED)
      .entity(toGridFeeResponse(saved))
      .build();
  }

  /**
   * Updates an existing grid fee.
   *
   * @param id      database ID
   * @param request updated values
   * @return updated fee, or 404 if not found
   */
  @PUT
  @Path("/grid-fees/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Update a grid fee")
  @Transactional
  public GridFeeResponse updateGridFee(
      @PathParam("id") long id, GridFeeRequest request) {
    GridFeeEntity update = toGridFeeEntity(request);
    return gridFeeRepository.update(id, update)
      .map(this::toGridFeeResponse)
      .orElseThrow(NotFoundException::new);
  }

  /**
   * Deletes a grid fee.
   *
   * @param id database ID
   */
  @DELETE
  @Path("/grid-fees/{id}")
  @Operation(summary = "Delete a grid fee")
  @Transactional
  public Response deleteGridFee(@PathParam("id") long id) {
    GridFeeEntity entity = gridFeeRepository.findById(id);
    if (entity == null) {
      throw new NotFoundException();
    }
    gridFeeRepository.delete(entity);
    return Response.noContent().build();
  }

  // ---- Fixed costs -------------------------------------------------------

  /**
   * Lists all fixed cost entries ordered by {@code validFrom} ascending.
   *
   * @return fixed cost list
   */
  @GET
  @Path("/fixed-costs")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List all fixed costs")
  public List<FixedCostResponse> listFixedCosts() {
    return fixedCostRepository.listAll(Sort.ascending("validFrom")).stream()
      .map(this::toFixedCostResponse)
      .toList();
  }

  /**
   * Creates a new fixed cost entry active from the given date.
   *
   * @param request cost details including {@code validFrom} date
   * @return the persisted entry
   */
  @POST
  @Path("/fixed-costs")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Create fixed cost entry")
  @Transactional
  public FixedCostResponse createFixedCost(FixedCostRequest request) {
    FixedCostEntity fee = new FixedCostEntity();
    fee.validFrom = parseDate(request.validFrom());
    fee.direction = parseFeeAppliesTo(request.direction());
    fee.monthlyCostEur = request.monthlyCostEur();
    fee.description = request.description();
    fixedCostRepository.save(fee);
    return toFixedCostResponse(fee);
  }

  /**
   * Updates an existing fixed cost entry.
   *
   * @param id      database ID
   * @param request updated values
   * @return updated entry, or 404 if not found
   */
  @PUT
  @Path("/fixed-costs/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Update fixed cost entry")
  @Transactional
  public FixedCostResponse updateFixedCost(@PathParam("id") long id, FixedCostRequest request) {
    FixedCostEntity update = new FixedCostEntity();
    update.validFrom = parseDate(request.validFrom());
    update.direction = parseFeeAppliesTo(request.direction());
    update.monthlyCostEur = request.monthlyCostEur();
    update.description = request.description();
    return fixedCostRepository.update(id, update)
      .map(this::toFixedCostResponse)
      .orElseThrow(NotFoundException::new);
  }

  /**
   * Deletes a fixed cost entry by ID.
   *
   * @param id the entry ID
   */
  @DELETE
  @Path("/fixed-costs/{id}")
  @Operation(summary = "Delete fixed cost entry")
  @Transactional
  public Response deleteFixedCost(@PathParam("id") long id) {
    FixedCostEntity entity = fixedCostRepository.findById(id);
    if (entity == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    fixedCostRepository.delete(entity);
    return Response.noContent().build();
  }

  // ---- Mapping helpers ---------------------------------------------------

  private CostControlConfigResponse toConfigResponse(CostControlConfigEntity e) {
    return new CostControlConfigResponse(
      e.importProviderId,
      e.exportProviderId,
      e.importFetchCron,
      e.exportFetchCron,
      e.sampleIntervalSeconds,
      e.deadBandWatts,
      e.retentionHourlyDays,
      e.retentionMonthlyYears,
      e.updatedAt != null ? e.updatedAt.toString() : null
    );
  }

  private EnergyPriceResponse toPriceResponse(EnergyPriceEntity e) {
    return new EnergyPriceResponse(
      e.startTime.toString(),
      e.endTime.toString(),
      e.priceImportCt,
      e.priceExportCt,
      e.importSource,
      e.exportSource,
      e.createdAt != null ? e.createdAt.toString() : null,
      e.updatedAt != null ? e.updatedAt.toString() : null
    );
  }

  private HourlyCostResponse toHourlyCostResponse(HourlyCostEntity e) {
    return new HourlyCostResponse(
      e.hourStart.toString(),
      e.hourEnd.toString(),
      e.importKwh,
      e.exportKwh,
      e.priceImportCt,
      e.priceExportCt,
      e.importPriceSource,
      e.exportPriceSource,
      e.importCostEur,
      e.exportIncomeEur,
      e.feeEur,
      e.netCostEur
    );
  }

  private MonthlyCostResponse toMonthlyCostResponse(MonthlyCostEntity e) {
    return new MonthlyCostResponse(
      e.yearMonth,
      e.totalImportKwh,
      e.totalExportKwh,
      e.totalImportCostEur,
      e.totalExportIncomeEur,
      e.totalFeeEur,
      e.fixedCostEur,
      e.netCostEur,
      e.hoursCalculated,
      e.updatedAt != null ? e.updatedAt.toString() : null
    );
  }

  private TariffWindowResponse toTariffWindowResponse(TariffWindowEntity e) {
    return new TariffWindowResponse(
      e.id,
      e.direction.name(),
      e.validFrom.toString(),
      e.validTo != null ? e.validTo.toString() : null,
      e.daysOfWeek,
      e.timeFrom.toString(),
      e.timeTo.toString(),
      e.priceCt,
      e.priority,
      e.description
    );
  }

  private TariffWindowEntity toTariffWindowEntity(TariffWindowRequest r) {
    TariffWindowEntity e = new TariffWindowEntity();
    e.direction = parsePriceDirection(r.direction());
    e.validFrom = parseDate(r.validFrom());
    e.validTo = (r.validTo() != null && !r.validTo().isBlank()) ? parseDate(r.validTo()) : null;
    e.daysOfWeek = r.daysOfWeek();
    e.timeFrom = java.time.LocalTime.parse(r.timeFrom());
    e.timeTo = java.time.LocalTime.parse(r.timeTo());
    e.priceCt = r.priceCt();
    e.priority = r.priority();
    e.description = r.description();
    return e;
  }

  private GridFeeResponse toGridFeeResponse(GridFeeEntity e) {
    return new GridFeeResponse(
      e.id,
      e.validFrom.toString(),
      e.feeType.name(),
      e.feeValue,
      e.appliesTo.name(),
      e.description
    );
  }

  private GridFeeEntity toGridFeeEntity(GridFeeRequest r) {
    GridFeeEntity e = new GridFeeEntity();
    e.validFrom = parseDateTime(r.validFrom());
    e.feeType = parseFeeType(r.feeType());
    e.feeValue = r.feeValue();
    e.appliesTo = parseFeeAppliesTo(r.appliesTo());
    e.description = r.description();
    return e;
  }

  private FixedCostResponse toFixedCostResponse(FixedCostEntity e) {
    return new FixedCostResponse(
      e.id,
      e.direction != null ? e.direction.name() : "BOTH",
      e.validFrom.toString(),
      e.monthlyCostEur,
      e.description
    );
  }

  // ---- Parse helpers -----------------------------------------------------

  private static PriceDirection parsePriceDirection(String value) {
    try {
      return PriceDirection.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid direction: " + value + "; expected IMPORT or EXPORT");
    }
  }

  private static FeeType parseFeeType(String value) {
    try {
      return FeeType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid feeType: " + value +
        "; expected PERCENT, ABSOLUTE_ENERGY, or ABSOLUTE_TIME");
    }
  }

  private static FeeAppliesTo parseFeeAppliesTo(String value) {
    try {
      return FeeAppliesTo.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid appliesTo: " + value +
        "; expected IMPORT, EXPORT, or BOTH");
    }
  }

  private static LocalDateTime parseDateTime(String value) {
    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequestException("Invalid date-time: " + value + "; expected ISO 8601 local");
    }
  }

  private static LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequestException("Invalid date: " + value + "; expected yyyy-MM-dd");
    }
  }
}
