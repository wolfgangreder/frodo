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
};

export default sunspecApi;
