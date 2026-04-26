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
