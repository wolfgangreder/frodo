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

package at.or.reder.frodo.modbus.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Hourly market price from aWATTar AT stock exchange.
 *
 * <p>Retrieved from {@code https://api.awattar.at/v1/marketdata}.
 * Each row represents one hour of prices, indexed by the hour start time.</p>
 *
 * <p>Stored in the database for use by the price-controlled export strategy.</p>
 */
@Entity
@Table(
    name = "FroMarketPrice",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_FroMarketPrice_start",
        columnNames = {"start_time"}
    )
)
public class MarketPriceEntity extends PanacheEntity {

    /**
     * Start of the hour this price applies to.
     */
    @Column(name = "start_time", nullable = false)
    public LocalDateTime startTime;

    /**
     * End of the hour this price applies to.
     */
    @Column(name = "end_time", nullable = false)
    public LocalDateTime endTime;

    /**
     * Market price in ct/kWh (euro-cents per kilowatt-hour).
     *
     * <p>Conversion from aWATTar API (EUR/MWh): {@code priceCt = priceEurMwh / 10}.
     * Negative values indicate hours where grid operators pay producers to consume.</p>
     */
    @Column(name = "price_ct", nullable = false, precision = 15, scale = 5)
    public BigDecimal priceCt;

    /**
     * Timestamp when this record was fetched from aWATTar.
     */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** JPA lifecycle: set timestamp on insert. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}