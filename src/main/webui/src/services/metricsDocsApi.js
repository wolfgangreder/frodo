import apiClient from './apiClient';

/**
 * Metrics documentation API service
 */
const metricsDocsApi = {
  /**
   * Get all semantic metric definitions with descriptions, units, and field mappings
   * @returns {Promise<Object>} Metrics documentation response
   */
  getDocs: async () => {
    const response = await apiClient.get('/metrics-docs');
    return response.data;
  },
};

export default metricsDocsApi;
