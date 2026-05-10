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

import apiClient from './apiClient';

/**
 * API service for the global price-controlled export setting.
 * Maps to the REST resource at /api/price-control.
 */
const priceControlApi = {
  /**
   * Fetch the current global price-control setting.
   * @returns {Promise<Object>} PriceControlResponse
   */
  getSetting: async () => {
    const response = await apiClient.get('/price-control');
    return response.data;
  },

  /**
   * Create or replace the global price-control setting.
   * @param {Object} payload - { enabled: boolean, exportToleranceWatts?: number }
   * @returns {Promise<Object>} Updated PriceControlResponse
   */
  setSetting: async (payload) => {
    const response = await apiClient.put('/price-control', payload);
    return response.data;
  },
};

export default priceControlApi;
