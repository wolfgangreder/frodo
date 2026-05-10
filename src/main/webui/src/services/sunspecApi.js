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
 * SunSpec API service for protocol-specific operations
 */
const sunspecApi = {
  /**
   * Discover SunSpec models on a device
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Discovery result with model list
   */
  discover: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/discovery`);
    return response.data;
  },

  /**
   * Get Common model data (Model 1)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Common model data
   */
  getCommon: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/common`);
    return response.data;
  },

  /**
   * Get Inverter model data (auto-detected 101-103, 111-113)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Inverter model data
   */
  getInverter: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/inverter`);
    return response.data;
  },

  /**
   * Get Nameplate model data (Model 120)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Nameplate ratings
   */
  getNameplate: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/nameplate`);
    return response.data;
  },

  /**
   * Get Settings model data (Model 121)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Basic settings
   */
  getSettings: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/settings`);
    return response.data;
  },

  /**
   * Get Status model data (Model 122)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Extended measurements and status
   */
  getStatus: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/status`);
    return response.data;
  },

  /**
   * Get Controls model data (Model 123)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Immediate controls
   */
  getControls: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/controls`);
    return response.data;
  },

  /**
   * Get Storage model data (Model 124)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Basic storage controls
   */
  getStorage: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/storage`);
    return response.data;
  },

  /**
   * Get MPPT model data (Model 160)
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object>} Multiple MPPT extension
   */
  getMppt: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/mppt`);
    return response.data;
  },

  /**
   * Get specific model by ID
   * @param {number} deviceId - Device ID
   * @param {number} modelId - SunSpec model ID
   * @returns {Promise<Object>} Model data
   */
  getModel: async (deviceId, modelId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/model/${modelId}`);
    return response.data;
  },

  /**
   * List all available models on a device
   * @param {number} deviceId - Device ID
   * @returns {Promise<Array>} List of available model IDs
   */
  listModels: async (deviceId) => {
    const response = await apiClient.get(`/devices/${deviceId}/sunspec/models`);
    return response.data;
  },

  /**
   * Activates or deactivates the inverter power limit via Model 123.
   *
   * When enable=true:
   *   - If limitWatts is provided (≥ 1): server applies a fixed watt cap
   *     (limitPct = limitWatts / WMax × 100). No Smart Meter needed.
   *   - Otherwise: server reads the Smart Meter and computes a closed-loop
   *     zero-export (Nulleinspeisung) limit.
   *
   * @param {number}  deviceId      - Device ID (inverter)
   * @param {boolean} enable        - true to activate limit, false to deactivate
   * @param {number}  [limitWatts]  - Fixed watt cap (≥ 1); omit for dynamic Smart Meter mode
   * @param {number}  [rampSeconds]   - Optional ramp time in seconds (0 = immediate)
   * @param {number}  [revertSeconds] - Optional auto-revert timeout in seconds (0 = no revert)
   * @returns {Promise<void>}
   */
  setPowerLimit: async (deviceId, enable, limitWatts, rampSeconds = 0, revertSeconds = 0) => {
    const body = {
      enable,
      rampSeconds: rampSeconds || null,
      revertSeconds: revertSeconds || null,
    };
    if (limitWatts != null && limitWatts >= 1) {
      body.limitWatts = limitWatts;
    }
    await apiClient.post(`/devices/${deviceId}/sunspec/controls/power-limit`, body);
  },

  // ========== Export Schedule ==========

  /**
   * Get the daily recurring grid-export schedule for a device.
   * Resolves to null (404) when no schedule is configured.
   *
   * @param {number} deviceId - Device ID
   * @returns {Promise<Object|null>} Schedule object or null
   */
  getExportSchedule: async (deviceId) => {
    try {
      const response = await apiClient.get(
        `/devices/${deviceId}/sunspec/controls/power-limit/schedule`
      );
      return response.data;
    } catch (err) {
      if (err?.response?.status === 404) return null;
      throw err;
    }
  },

  /**
   * Create or replace the daily recurring grid-export schedule.
   *
   * @param {number}  deviceId          - Device ID
   * @param {Object}  body              - Schedule payload
   * @param {boolean} body.enabled      - Whether the schedule is active
   * @param {string}  [body.blockFrom]  - "HH:mm" time to start blocking (omit for PRICE_CONTROLLED)
   * @param {string}  [body.enableFrom] - "HH:mm" time to re-enable export (omit for PRICE_CONTROLLED)
   * @param {string}  [body.strategy]   - Strategy enum value
   * @param {number}  [body.limitWatts] - Fixed power cap in Watts (FIXED_LIMIT only)
   * @param {number}  [body.exportToleranceWatts] - Export buffer in Watts (PRICE_CONTROLLED only)
   * @returns {Promise<Object>} Saved schedule
   */
  setExportSchedule: async (deviceId, body) => {
    const response = await apiClient.put(
      `/devices/${deviceId}/sunspec/controls/power-limit/schedule`,
      body
    );
    return response.data;
  },

  /**
   * Delete the grid-export schedule for a device.
   *
   * @param {number} deviceId - Device ID
   * @returns {Promise<void>}
   */
  deleteExportSchedule: async (deviceId) => {
    await apiClient.delete(
      `/devices/${deviceId}/sunspec/controls/power-limit/schedule`
    );
  },
};

export default sunspecApi;
