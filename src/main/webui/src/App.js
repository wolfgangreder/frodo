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

import React from 'react';
import { Routes, Route } from 'react-router-dom';
import { AppShell } from './components/layout';
import {
  DashboardPage,
  DevicesPage,
  GpioPage,
  MetricsConfigPage,
  MetricsDocsPage,
  GrafanaPage,
  SolarApiPage,
  CostControlPage,
  SettingsPage,
  AboutPage,
  NotFoundPage,
} from './pages';

/**
 * Main App component with routing configuration
 */
function App() {
  return (
    <Routes>
      {/* Main layout with sidebar */}
      <Route path="/" element={<AppShell />}>
        {/* Dashboard - home page */}
        <Route index element={<DashboardPage />} />

        {/* Device management */}
        <Route path="devices" element={<DevicesPage />} />
        <Route path="devices/:id" element={<DevicesPage />} />

        {/* Metrics configuration per device */}
        <Route path="devices/:id/metrics" element={<MetricsConfigPage />} />

        {/* Metrics documentation */}
        <Route path="metrics-docs" element={<MetricsDocsPage />} />

        {/* Grafana dashboards */}
        <Route path="grafana" element={<GrafanaPage />} />

        {/* GPIO Export Control */}
        <Route path="gpio" element={<GpioPage />} />

        {/* Solar API */}
        <Route path="solar-api" element={<SolarApiPage />} />

        {/* Cost Control */}
        <Route path="cost-control" element={<CostControlPage />} />

        {/* Settings */}
        <Route path="settings" element={<SettingsPage />} />

        {/* About */}
        <Route path="about" element={<AboutPage />} />

        {/* 404 catch-all */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default App;
