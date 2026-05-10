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

/**
 * Grafana service for building embed URLs and managing Grafana settings.
 *
 * Grafana must be configured with:
 *   GF_SECURITY_ALLOW_EMBEDDING=true
 *   GF_AUTH_ANONYMOUS_ENABLED=true
 *   GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
 *
 * Default: Grafana at http://localhost:3000
 */

const GRAFANA_BASE_URL_KEY = 'frodo.grafana.baseUrl';
const DEFAULT_GRAFANA_URL = 'http://localhost:3000';

const grafanaService = {
  /**
   * Get the configured Grafana base URL (from localStorage or default).
   * @returns {string}
   */
  getBaseUrl: () => {
    return localStorage.getItem(GRAFANA_BASE_URL_KEY) || DEFAULT_GRAFANA_URL;
  },

  /**
   * Persist a new Grafana base URL to localStorage.
   * @param {string} url
   */
  setBaseUrl: (url) => {
    localStorage.setItem(GRAFANA_BASE_URL_KEY, url.replace(/\/$/, ''));
  },

  /**
   * Reset Grafana base URL to default.
   */
  resetBaseUrl: () => {
    localStorage.removeItem(GRAFANA_BASE_URL_KEY);
  },

  /**
   * Build a panel embed URL (uses /d-solo for single-panel embeds).
   *
   * @param {object} config
   * @param {string} config.dashboardUid  Grafana dashboard UID
   * @param {number|string} config.panelId  Panel ID within the dashboard
   * @param {string} [config.from='now-1h']  Time range start
   * @param {string} [config.to='now']  Time range end
   * @param {string} [config.refresh='30s']  Auto-refresh interval
   * @param {string} [config.theme='dark']  'dark' | 'light'
   * @param {string} [config.orgId='1']  Grafana org ID
   * @returns {string} Grafana panel embed URL
   */
  buildPanelUrl: ({
    dashboardUid,
    panelId,
    from = 'now-1h',
    to = 'now',
    refresh = '30s',
    theme = 'dark',
    orgId = '1',
  }) => {
    const base = grafanaService.getBaseUrl();
    const params = new URLSearchParams({
      orgId,
      panelId: String(panelId),
      from,
      to,
      refresh,
      theme,
    });
    return `${base}/d-solo/${dashboardUid}?${params.toString()}`;
  },

  /**
   * Build a full dashboard URL.
   * @param {string} dashboardUid
   * @param {object} [options]
   * @param {string} [options.from='now-1h']
   * @param {string} [options.to='now']
   * @param {string} [options.theme='dark']
   * @returns {string}
   */
  buildDashboardUrl: (dashboardUid, { from = 'now-1h', to = 'now', theme = 'dark' } = {}) => {
    const base = grafanaService.getBaseUrl();
    const params = new URLSearchParams({ from, to, theme });
    return `${base}/d/${dashboardUid}?${params.toString()}`;
  },

  /**
   * Test whether Grafana is reachable at the configured URL.
   * Uses the Grafana health endpoint (no auth required).
   * @returns {Promise<boolean>}
   */
  testConnection: async () => {
    const base = grafanaService.getBaseUrl();
    try {
      const response = await fetch(`${base}/api/health`, {
        mode: 'cors',
        signal: AbortSignal.timeout(5000),
      });
      return response.ok;
    } catch {
      return false;
    }
  },
};

export default grafanaService;
