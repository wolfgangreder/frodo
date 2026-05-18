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

// Non-application management endpoints live under the same root path as the app
// (quarkus.http.root-path) but outside /api. Derive their prefix from Vite's BASE_URL.
const _base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '');

/**
 * System API service for application info, health, and pool status
 */
const systemApi = {
  /**
   * Get application info
   * @returns {Promise<Object>} Application info (name, version, description)
   */
  getInfo: async () => {
    const response = await apiClient.get('/info');
    return response.data;
  },

  /**
   * Get health status
   * Note: This endpoint is at /q/health, not /api/health
   * @returns {Promise<Object>} Health check result
   */
  getHealth: async () => {
    const response = await apiClient.get(`${_base}/q/health/ready`, {
      baseURL: '', // override: path is already absolute
    });
    return response.data;
  },

  /**
   * Get Modbus connection pool and scraping status
   * @returns {Promise<Object>} Pool status (connectionState, activeConnections, etc.)
   */
  getPoolStatus: async () => {
    const response = await apiClient.get('/status/pool');
    return response.data;
  },

  /**
   * Get Prometheus metrics
   * Note: This returns text/plain format
   * @returns {Promise<string>} Prometheus metrics text
   */
  getMetrics: async () => {
    const response = await apiClient.get(`${_base}/q/metrics`, {
      baseURL: '', // override: path is already absolute
      headers: {
        Accept: 'text/plain',
      },
    });
    return response.data;
  },
};

export default systemApi;
