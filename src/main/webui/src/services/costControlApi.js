import apiClient from './apiClient';

/**
 * API service for energy cost control.
 * Maps to REST resource at /api/cost-control.
 */
const costControlApi = {
  // ---- Config ------------------------------------------------------------

  /** Get current cost control configuration. */
  getConfig: async () => {
    const response = await apiClient.get('/cost-control/config');
    return response.data;
  },

  /** Update cost control configuration. */
  updateConfig: async (payload) => {
    const response = await apiClient.put('/cost-control/config', payload);
    return response.data;
  },

  // ---- Providers ---------------------------------------------------------

  /** List all registered energy price providers. */
  listProviders: async () => {
    const response = await apiClient.get('/cost-control/providers');
    return response.data;
  },

  // ---- Energy prices -----------------------------------------------------

  /**
   * Get recent hourly energy prices.
   * @param {number} [limit=24] max entries (1-48)
   */
  getRecentPrices: async (limit = 24) => {
    const response = await apiClient.get('/cost-control/prices', { params: { limit } });
    return response.data;
  },

  /**
   * Force-refresh prices for a direction.
   * @param {'IMPORT'|'EXPORT'} direction
   */
  refreshPrices: async (direction) => {
    await apiClient.post(`/cost-control/prices/refresh/${direction}`);
  },

  /**
   * Set manual import price for an hour.
   * @param {{ hourStart: string, priceCt: number }} payload
   */
  setImportPrice: async (payload) => {
    const response = await apiClient.put('/cost-control/prices/import', payload);
    return response.data;
  },

  /**
   * Set manual export price for an hour.
   * @param {{ hourStart: string, priceCt: number }} payload
   */
  setExportPrice: async (payload) => {
    const response = await apiClient.put('/cost-control/prices/export', payload);
    return response.data;
  },

  // ---- Hourly cost -------------------------------------------------------

  /**
   * Get hourly cost records in date range.
   * @param {string} [from] ISO local (e.g. 2026-05-01T00:00:00)
   * @param {string} [to]   ISO local
   */
  getHourlyCosts: async (from, to) => {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/cost-control/hourly', { params });
    return response.data;
  },

  /** Get the latest completed hourly cost record. */
  getLatestHourlyCost: async () => {
    const response = await apiClient.get('/cost-control/hourly/latest');
    return response.data;
  },

  // ---- Monthly cost ------------------------------------------------------

  /** Get all monthly cost summaries (newest first). */
  getMonthlyCosts: async () => {
    const response = await apiClient.get('/cost-control/monthly');
    return response.data;
  },

  /**
   * Get monthly cost summary for a specific month.
   * @param {string} yearMonth format yyyy-MM
   */
  getMonthlyCost: async (yearMonth) => {
    const response = await apiClient.get(`/cost-control/monthly/${yearMonth}`);
    return response.data;
  },

  // ---- Tariff windows ----------------------------------------------------

  /**
   * List tariff windows.
   * @param {'IMPORT'|'EXPORT'|undefined} direction optional filter
   */
  listTariffWindows: async (direction) => {
    const params = direction ? { direction } : {};
    const response = await apiClient.get('/cost-control/tariff-windows', { params });
    return response.data;
  },

  /** Create a tariff window. */
  createTariffWindow: async (payload) => {
    const response = await apiClient.post('/cost-control/tariff-windows', payload);
    return response.data;
  },

  /**
   * Update a tariff window.
   * @param {number} id
   * @param {Object} payload
   */
  updateTariffWindow: async (id, payload) => {
    const response = await apiClient.put(`/cost-control/tariff-windows/${id}`, payload);
    return response.data;
  },

  /**
   * Delete a tariff window.
   * @param {number} id
   */
  deleteTariffWindow: async (id) => {
    await apiClient.delete(`/cost-control/tariff-windows/${id}`);
  },

  // ---- Grid fees ---------------------------------------------------------

  /** List all grid fees. */
  listGridFees: async () => {
    const response = await apiClient.get('/cost-control/grid-fees');
    return response.data;
  },

  /** Create a grid fee. */
  createGridFee: async (payload) => {
    const response = await apiClient.post('/cost-control/grid-fees', payload);
    return response.data;
  },

  /**
   * Update a grid fee.
   * @param {number} id
   * @param {Object} payload
   */
  updateGridFee: async (id, payload) => {
    const response = await apiClient.put(`/cost-control/grid-fees/${id}`, payload);
    return response.data;
  },

  /**
   * Delete a grid fee.
   * @param {number} id
   */
  deleteGridFee: async (id) => {
    await apiClient.delete(`/cost-control/grid-fees/${id}`);
  },

  // ---- Fixed costs -------------------------------------------------------

  /** List all fixed costs ordered by validFrom ascending. */
  listFixedCosts: async () => {
    const response = await apiClient.get('/cost-control/fixed-costs');
    return response.data;
  },

  /**
   * Create a fixed cost entry active from the given date.
   * @param {{ validFrom: string, monthlyCostEur: number, description?: string }} payload
   */
  createFixedCost: async (payload) => {
    const response = await apiClient.post('/cost-control/fixed-costs', payload);
    return response.data;
  },

  /**
   * Delete a fixed cost entry by ID.
   * @param {number} id
   */
  deleteFixedCost: async (id) => {
    await apiClient.delete(`/cost-control/fixed-costs/${id}`);
  },

  /**
   * Update an existing fixed cost entry.
   * @param {number} id
   * @param {{ validFrom: string, direction: string, monthlyCostEur: number, description?: string }} payload
   */
  updateFixedCost: async (id, payload) => {
    const response = await apiClient.put(`/cost-control/fixed-costs/${id}`, payload);
    return response.data;
  },
};

export default costControlApi;
