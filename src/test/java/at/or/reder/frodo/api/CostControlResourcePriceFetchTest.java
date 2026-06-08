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

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Endpoint tests for POST /api/cost-control/prices/fetch/{direction}.
 *
 * <p>These tests use the test profile (datasource inactive) and therefore
 * cover validation (400) and disabled-state (409) responses without a real DB.</p>
 */
@QuarkusTest
class CostControlResourcePriceFetchTest {

  @Test
  void fetchPricesForDay_missingDate_returns400() {
    given()
      .when().post("/api/cost-control/prices/fetch/IMPORT")
      .then()
      .statusCode(400);
  }

  @Test
  void fetchPricesForDay_invalidDate_returns400() {
    given()
      .queryParam("date", "not-a-date")
      .when().post("/api/cost-control/prices/fetch/IMPORT")
      .then()
      .statusCode(400);
  }

  @Test
  void fetchPricesForDay_invalidDirection_returns400() {
    given()
      .queryParam("date", "2026-06-07")
      .when().post("/api/cost-control/prices/fetch/BADDIR")
      .then()
      .statusCode(400);
  }

  @Test
  void fetchPricesForDay_datasourceInactive_returns409() {
    // In test profile quarkus.datasource.active=false, so the service
    // throws IllegalStateException which is mapped to 409 Conflict.
    given()
      .queryParam("date", "2026-06-07")
      .when().post("/api/cost-control/prices/fetch/IMPORT")
      .then()
      .statusCode(409);
  }
}
