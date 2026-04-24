import apiClient from './apiClient';

/**
 * Solar API service for fetching Solar API status and live power flow values
 */
const solarApiService = {
  /**
   * Get Solar API status and current power flow values
   * @returns {Promise<Object>} Solar API status with site, inverter, and Ohmpilot data
   */
  getStatus: async () => {
    const response = await apiClient.get('/solar-api/status');
    return response.data;
  },
};

export default solarApiService;
