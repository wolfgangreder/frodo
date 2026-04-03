import React from 'react';
import { Routes, Route } from 'react-router-dom';
import { AppShell } from './components/layout';
import {
  DashboardPage,
  DevicesPage,
  MetricsConfigPage,
  GrafanaPage,
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

        {/* Grafana dashboards */}
        <Route path="grafana" element={<GrafanaPage />} />

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
