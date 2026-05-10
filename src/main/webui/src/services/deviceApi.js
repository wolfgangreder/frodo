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
 * Device API service for CRUD operations and device information
 */
const deviceApi = {
  /**
   * Get all devices
   * @returns {Promise<Array>} List of device summaries
   */
  getAll: async () => {
    const response = await apiClient.get('/devices');
    return response.data.devices; // Extract devices array from DeviceListResponse
  },

  /**
   * Get a single device by ID
   * @param {number} id - Device ID
   * @returns {Promise<Object>} Device details
   */
  getById: async (id) => {
    const response = await apiClient.get(`/devices/${id}`);
    return response.data;
  },

  /**
   * Create a new device
   * @param {Object} device - Device data
   * @param {string} device.name - Display name
   * @param {string} device.host - Hostname or IP address
   * @param {number} device.port - Modbus TCP port (default 502)
   * @param {number} device.unitId - Modbus unit/slave ID
   * @param {boolean} device.enabled - Whether device is enabled
   * @returns {Promise<Object>} Created device
   */
  create: async (device) => {
    const response = await apiClient.post('/devices', device);
    return response.data;
  },

  /**
   * Update an existing device
   * @param {number} id - Device ID
   * @param {Object} device - Device data to update
   * @returns {Promise<Object>} Updated device
   */
  update: async (id, device) => {
    const response = await apiClient.put(`/devices/${id}`, device);
    return response.data;
  },

  /**
   * Delete a device
   * @param {number} id - Device ID
   * @returns {Promise<void>}
   */
  delete: async (id) => {
    await apiClient.delete(`/devices/${id}`);
  },

  /**
   * Get cached device identification info (FC 0x2B result)
   * @param {number} id - Device ID
   * @returns {Promise<Object>} Device identification
   */
  getInfo: async (id) => {
    const response = await apiClient.get(`/devices/${id}/info`);
    return response.data;
  },

  /**
   * Force refresh device identification from device
   * @param {number} id - Device ID
   * @returns {Promise<Object>} Fresh device identification
   */
  refreshInfo: async (id) => {
    const response = await apiClient.post(`/devices/${id}/info/refresh`);
    return response.data;
  },

  /**
   * Test connection to a device (without saving)
   * Note: This endpoint may need to be implemented on the backend
   * @param {Object} connectionParams - Connection parameters
   * @param {string} connectionParams.host - Hostname or IP address
   * @param {number} connectionParams.port - Modbus TCP port
   * @param {number} connectionParams.unitId - Modbus unit/slave ID
   * @returns {Promise<Object>} Connection test result
   */
  testConnection: async (connectionParams) => {
    const response = await apiClient.post('/devices/test', connectionParams);
    return response.data;
  },
};

export default deviceApi;
