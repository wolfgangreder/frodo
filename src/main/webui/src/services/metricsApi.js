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
 * Metrics API service for per-device metrics configuration and data
 */
const metricsApi = {
  /**
   * Get metrics configuration for a device
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Metrics configuration
   */
  getConfig: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/metrics/config`);
    return response.data;
  },

  /**
   * Update metrics configuration for a device
   * @param {number} deviceId - Device ID
   * @param {Object} config - Configuration data
   * @param {number} config.scrapeIntervalSeconds - Scrape interval in seconds
   * @param {boolean} config.enabled - Whether scraping is enabled
   * @param {boolean} [config.storeToDatabase] - Whether to persist data to DB
   * @param {number} [config.retentionDays] - Data retention period in days
   * @param {Array} [config.parameters] - Parameter configurations
   * @returns {Promise<Object>} Updated metrics configuration
   */
  updateConfig: async (deviceId, config) => {
    const response = await apiClient.put(`/devices/${deviceId}/metrics/config`, config);
    return response.data;
  },

  /**
   * Get available SunSpec parameters for metrics collection
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Available parameters grouped by model
   */
  getAvailableParameters: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/metrics/available-parameters`);
    return response.data;
  },

  /**
   * Get current scraping status for a device
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Scraping status
   */
  getStatus: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/metrics/status`);
    return response.data;
  },

  /**
   * Get historical metrics data for a device
   * @param {number} deviceId - Device ID
   * @param {Object} [params] - Query parameters
   * @param {string} [params.from] - Start of time range (ISO-8601)
   * @param {string} [params.to] - End of time range (ISO-8601)
   * @param {number} [params.modelId] - SunSpec model ID filter
   * @param {string} [params.field] - Field name filter
   * @param {number} [params.limit] - Max results (default 1000)
   * @returns {Promise<Object>} Historical data points
   */
  getData: async (deviceId, params = {}) => {
    const response = await apiClient.get(`/devices/${deviceId}/metrics/data`, { params });
    return response.data;
  },

  /**
   * Get latest metrics values for a device
   * @param {number} deviceId - Device ID
   * @param {number} [limit=100] - Max results
   * @returns {Promise<Object>} Latest metrics values
   */
  getLatestData: async (deviceId, limit = 100) => {
    const response = await apiClient.get(`/devices/${deviceId}/metrics/data/latest`, {
      params: { limit },
    });
    return response.data;
  },
};

export default metricsApi;
