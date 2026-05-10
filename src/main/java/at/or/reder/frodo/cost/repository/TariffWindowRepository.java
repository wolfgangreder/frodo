package at.or.reder.frodo.cost.repository;

import at.or.reder.frodo.cost.entity.TariffWindowEntity;
import at.or.reder.frodo.cost.spi.PriceDirection;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Repository for {@link TariffWindowEntity} — fixed-price time slots.
 */
@ApplicationScoped
public class TariffWindowRepository implements PanacheRepository<TariffWindowEntity> {

  /**
   * Finds the highest-priority tariff window that matches the given direction and hour.
   *
   * <p>A window matches when all of the following hold:
   * <ol>
   *   <li>{@code direction} equals the given direction</li>
   *   <li>{@code validFrom} ≤ {@code hourStart.date}</li>
   *   <li>{@code validTo} is null OR {@code validTo} > {@code hourStart.date}</li>
   *   <li>{@code daysOfWeek} is null OR contains the weekday of {@code hourStart}</li>
   *   <li>{@code timeFrom} ≤ {@code hourStart.time} AND
   *       ({@code timeTo = 00:00} OR {@code timeTo} > {@code hourStart.time})</li>
   * </ol>
   *
   * @param direction price direction
   * @param hourStart start of the hour to match
   * @return highest-priority matching window, or empty
   */
  public Optional<TariffWindowEntity> findMatchingWindow(
      PriceDirection direction, LocalDateTime hourStart) {
    LocalDate date = hourStart.toLocalDate();
    LocalTime time = hourStart.toLocalTime();
    String dayName = hourStart.getDayOfWeek()
      .getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);

    // Load candidates: direction + validFrom <= date + (validTo is null OR validTo > date)
    List<TariffWindowEntity> candidates = list(
      "direction = ?1 and validFrom <= ?2 and (validTo is null or validTo > ?2)",
      direction, date);

    return candidates.stream()
      .filter(w -> matchesDayOfWeek(w, dayName))
      .filter(w -> matchesTime(w, time))
      .max(Comparator.comparingInt(w -> w.priority));
  }

  /**
   * Lists all tariff windows for a given direction, ordered by {@code validFrom} desc.
   *
   * @param direction price direction
   * @return list of windows
   */
  public List<TariffWindowEntity> listByDirection(PriceDirection direction) {
    return list("direction = ?1 order by validFrom desc", direction);
  }

  /**
   * Persists a new tariff window.
   *
   * @param window the window entity to persist
   * @return the persisted entity
   */
  @Transactional
  public TariffWindowEntity save(TariffWindowEntity window) {
    persist(window);
    return window;
  }

  /**
   * Updates an existing tariff window by id.
   *
   * @param id             window id
   * @param updatedWindow  entity with updated values
   * @return updated entity, or empty if not found
   */
  @Transactional
  public Optional<TariffWindowEntity> update(long id, TariffWindowEntity updatedWindow) {
    TariffWindowEntity existing = findById(id);
    if (existing == null) {
      return Optional.empty();
    }
    existing.direction = updatedWindow.direction;
    existing.validFrom = updatedWindow.validFrom;
    existing.validTo = updatedWindow.validTo;
    existing.daysOfWeek = updatedWindow.daysOfWeek;
    existing.timeFrom = updatedWindow.timeFrom;
    existing.timeTo = updatedWindow.timeTo;
    existing.priceCt = updatedWindow.priceCt;
    existing.priority = updatedWindow.priority;
    existing.description = updatedWindow.description;
    return Optional.of(existing);
  }

  // ---- helpers -----------------------------------------------------------

  private static boolean matchesDayOfWeek(TariffWindowEntity w, String dayName) {
    if (w.daysOfWeek == null || w.daysOfWeek.isBlank()) {
      return true; // null = all days
    }
    for (String d : w.daysOfWeek.split(",")) {
      if (d.trim().equalsIgnoreCase(dayName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesTime(TariffWindowEntity w, LocalTime time) {
    // timeFrom <= time (start is inclusive)
    if (time.isBefore(w.timeFrom)) {
      return false;
    }
    // timeTo = 00:00 means end-of-day (matches all times from timeFrom to midnight)
    if (w.timeTo.equals(LocalTime.MIDNIGHT)) {
      return true;
    }
    // time < timeTo (end is exclusive)
    return time.isBefore(w.timeTo);
  }
}
