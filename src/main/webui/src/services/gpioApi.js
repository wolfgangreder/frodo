import apiClient from './apiClient';

/**
 * API service for GPIO export control.
 * Maps to the REST resource at /api/gpio.
 */
const gpioApi = {
  /**
   * Get full GPIO system + per-pair status.
   * @returns {Promise<Object>} GpioStatusDto
   */
  getStatus: async () => {
    const response = await apiClient.get('/gpio/status');
    return response.data;
  },

  /**
   * List all configured pair names.
   * @returns {Promise<string[]>}
   */
  getPairs: async () => {
    const response = await apiClient.get('/gpio/pairs');
    return response.data;
  },

  /**
   * Set manual output override for a pair.
   * @param {string} name - pair name
   * @param {boolean} high - true = HIGH, false = LOW
   */
  setManualOutput: async (name, high) => {
    const response = await apiClient.put(`/gpio/pairs/${name}/output`, { high });
    return response.data;
  },

  /**
   * Clear manual output override for a pair.
   * @param {string} name - pair name
   */
  clearManualOutput: async (name) => {
    const response = await apiClient.delete(`/gpio/pairs/${name}/output`);
    return response.data;
  },

  /**
   * List all device-to-pair assignments.
   * @returns {Promise<Object[]>} GpioAssignmentDto[]
   */
  getAssignments: async () => {
    const response = await apiClient.get('/gpio/assignments');
    return response.data;
  },

  /**
   * Get assignment for a specific device.
   * @param {number} deviceId
   * @returns {Promise<Object>} GpioAssignmentDto
   */
  getAssignment: async (deviceId) => {
    const response = await apiClient.get(`/gpio/assignments/${deviceId}`);
    return response.data;
  },

  /**
   * Create or update a device-to-pair assignment.
   * @param {number} deviceId
   * @param {string} gpioPairName
   * @returns {Promise<Object>} GpioAssignmentDto
   */
  setAssignment: async (deviceId, gpioPairName) => {
    const response = await apiClient.put(`/gpio/assignments/${deviceId}`, { gpioPairName });
    return response.data;
  },

  /**
   * Remove a device-to-pair assignment.
   * @param {number} deviceId
   */
  deleteAssignment: async (deviceId) => {
    const response = await apiClient.delete(`/gpio/assignments/${deviceId}`);
    return response.data;
  },
};

export default gpioApi;
