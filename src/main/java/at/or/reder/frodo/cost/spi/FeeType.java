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

package at.or.reder.frodo.cost.spi;

/**
 * Grid fee calculation type.
 */
public enum FeeType {
  /**
   * Percentage of the base energy cost.
   * {@code feeValue} is a percentage (e.g. 5.0 = 5%).
   */
  PERCENT,

  /**
   * Absolute charge per kilowatt-hour.
   * {@code feeValue} is in ct/kWh.
   */
  ABSOLUTE_ENERGY,

  /**
   * Absolute charge per month, amortised per hour (÷ 730).
   * {@code feeValue} is in EUR/month.
   */
  ABSOLUTE_TIME
}
