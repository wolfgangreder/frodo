import apiClient from './apiClient';

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
    const response = await apiClient.get('/q/health/ready', {
      baseURL: '', // Override baseURL to use root path
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
    const response = await apiClient.get('/q/metrics', {
      baseURL: '', // Override baseURL to use root path
      headers: {
        Accept: 'text/plain',
      },
    });
    return response.data;
  },
};

export default systemApi;
