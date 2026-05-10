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
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import SolarPowerIcon from '@mui/icons-material/SolarPower';
import BoltIcon from '@mui/icons-material/Bolt';
import BatteryChargingFullIcon from '@mui/icons-material/BatteryChargingFull';
import ElectricalServicesIcon from '@mui/icons-material/ElectricalServices';
import HomeIcon from '@mui/icons-material/Home';
import WaterIcon from '@mui/icons-material/Water';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import { useSolarApiStatus } from '../hooks';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Format a watt value with auto-scaling to kW
 */
function formatPower(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(2)} kW`;
  return `${num.toFixed(1)} W`;
}

/**
 * Format a watt-hour value with auto-scaling to kWh/MWh
 */
function formatEnergy(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000000) return `${(num / 1000000).toFixed(2)} MWh`;
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(1)} kWh`;
  return `${num.toFixed(0)} Wh`;
}

/**
 * Format a percentage value
 */
function formatPercent(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  return `${parseFloat(value).toFixed(1)} %`;
}

/**
 * Format temperature in degrees Celsius
 */
function formatTemperature(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  return `${parseFloat(value).toFixed(1)} °C`;
}

/**
 * Capitalize first letter of a string
 */
function capitalize(str) {
  if (!str) return '-';
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

/**
 * Map Ohmpilot state to a MUI chip color
 */
function stateColor(state) {
  if (!state) return 'default';
  switch (state.toLowerCase()) {
    case 'normal': return 'success';
    case 'boost': return 'warning';
    case 'fault': return 'error';
    case 'startup': return 'info';
    case 'standby': return 'default';
    default: return 'default';
  }
}

// ---------------------------------------------------------------------------
// Shared metric row component
// ---------------------------------------------------------------------------

function MetricRow({ label, value, primary = false }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant={primary ? 'subtitle1' : 'body2'}
        sx={{ fontWeight: primary ? 700 : 500, fontFamily: 'monospace' }}
      >
        {value}
      </Typography>
    </Box>
  );
}

function MetricsSkeleton({ count }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {[...Array(count)].map((_, i) => (
        <Skeleton key={i} variant="text" width="100%" height={24} />
      ))}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Site Power Flow Card
// ---------------------------------------------------------------------------

function SitePowerFlowCard({ site, isLoading }) {
  const pvActive = site?.pvPowerWatts != null && site.pvPowerWatts > 0;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
          <SolarPowerIcon sx={{ color: pvActive ? 'success.main' : 'text.disabled' }} />
          <Typography variant="h6" sx={{ color: 'primary.main' }}>
            Site Power Flow
          </Typography>
        </Stack>

        {isLoading ? (
          <MetricsSkeleton count={6} />
        ) : !site ? (
          <Alert severity="info" variant="outlined">No site data available</Alert>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            <MetricRow
              label="PV Production"
              value={formatPower(site.pvPowerWatts)}
              primary
            />
            <MetricRow
              label="Grid"
              value={formatPower(site.gridPowerWatts)}
              primary
            />
            <MetricRow
              label="Load"
              value={formatPower(site.loadPowerWatts != null ? Math.abs(site.loadPowerWatts) : null)}
              primary
            />
            <MetricRow
              label="Battery"
              value={formatPower(site.batteryPowerWatts)}
            />
            <MetricRow
              label="Autonomy"
              value={formatPercent(site.autonomyPercent)}
            />
            <MetricRow
              label="Self-Consumption"
              value={formatPercent(site.selfConsumptionPercent)}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Per-inverter Card
// ---------------------------------------------------------------------------

function InverterCard({ inverter, isLoading }) {
  const isGenerating = inverter?.powerWatts != null && inverter.powerWatts > 0;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
          <BoltIcon sx={{ color: isGenerating ? 'success.main' : 'text.disabled' }} />
          <Typography variant="h6" sx={{ color: 'primary.main' }}>
            Inverter {inverter?.deviceId || '?'}
          </Typography>
          {inverter?.batteryMode && (
            <Chip
              label={capitalize(inverter.batteryMode)}
              size="small"
              variant="outlined"
              color="default"
              sx={{ ml: 'auto' }}
            />
          )}
        </Stack>

        {isLoading ? (
          <MetricsSkeleton count={3} />
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            <MetricRow
              label="AC Power"
              value={formatPower(inverter?.powerWatts)}
              primary
            />
            <MetricRow
              label="Energy Total"
              value={formatEnergy(inverter?.energyTotalWattHours)}
            />
            <MetricRow
              label="Battery SOC"
              value={formatPercent(inverter?.batterySOCPercent)}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Per-Ohmpilot Card
// ---------------------------------------------------------------------------

function OhmpilotCard({ ohmpilot, isLoading }) {
  const isActive = ohmpilot?.powerWatts != null && ohmpilot.powerWatts > 0;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
          <WaterIcon sx={{ color: isActive ? 'warning.main' : 'text.disabled' }} />
          <Typography variant="h6" sx={{ color: 'primary.main' }}>
            Ohmpilot {ohmpilot?.componentId || '?'}
          </Typography>
          {ohmpilot?.state && (
            <Chip
              label={capitalize(ohmpilot.state)}
              size="small"
              color={stateColor(ohmpilot.state)}
              sx={{ ml: 'auto' }}
            />
          )}
        </Stack>

        {isLoading ? (
          <MetricsSkeleton count={3} />
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            <MetricRow
              label="Power"
              value={formatPower(ohmpilot?.powerWatts)}
              primary
            />
            <MetricRow
              label="Temperature"
              value={formatTemperature(ohmpilot?.temperatureCelsius)}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Scraping Status Bar
// ---------------------------------------------------------------------------

function ScrapingStatusBar({ data }) {
  if (!data) return null;

  return (
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          alignItems={{ sm: 'center' }}
          justifyContent="space-between"
        >
          <Stack direction="row" spacing={1} alignItems="center">
            <Chip
              label={data.active ? 'Active' : 'Inactive'}
              color={data.active ? 'success' : 'default'}
              size="small"
            />
            <Typography variant="body2" color="text.secondary">
              Interval: {data.scrapeIntervalSeconds}s
            </Typography>
          </Stack>

          <Stack direction="row" spacing={2}>
            <Typography variant="body2" color="text.secondary">
              Scrapes: {data.scrapeCount.toLocaleString()}
            </Typography>
            {data.errorCount > 0 && (
              <Typography variant="body2" color="error">
                Errors: {data.errorCount.toLocaleString()}
              </Typography>
            )}
            {data.lastScrapeTime && (
              <Typography variant="body2" color="text.secondary">
                Last: {new Date(data.lastScrapeTime).toLocaleTimeString()}
              </Typography>
            )}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

function SolarApiPage() {
  const { data, isLoading, isError, error, refetch } = useSolarApiStatus();

  if (isLoading && !data) {
    return <LoadingSpinner message="Loading Solar API status..." fullPage />;
  }

  if (isError && !data) {
    return (
      <ErrorDisplay
        title="Failed to load Solar API status"
        message={error?.message}
        onRetry={refetch}
        fullPage
      />
    );
  }

  if (data && !data.enabled) {
    return (
      <Box>
        <PageHeader
          title="Solar API"
          subtitle="Fronius Solar API power flow metrics"
        />
        <Alert severity="info" sx={{ mt: 2 }}>
          Solar API integration is disabled. Set{' '}
          <Typography component="code" variant="body2" sx={{ fontFamily: 'monospace' }}>
            frodo.solar-api.enabled=true
          </Typography>{' '}
          in your configuration to enable live power flow metrics from the Fronius inverter.
        </Alert>
      </Box>
    );
  }

  const inverters = data?.inverters || [];
  const ohmpilots = data?.ohmpilots || [];

  return (
    <Box>
      <PageHeader
        title="Solar API"
        subtitle="Live power flow from Fronius Solar API"
      />

      {/* Scraping status */}
      <ScrapingStatusBar data={data} />

      <Grid container spacing={2}>
        {/* Site Power Flow - full width on small, half on medium+ */}
        <Grid size={{ xs: 12, md: 6 }}>
          <SitePowerFlowCard site={data?.site} isLoading={isLoading && !data} />
        </Grid>

        {/* Inverters */}
        {inverters.map((inv) => (
          <Grid key={inv.deviceId} size={{ xs: 12, sm: 6, md: 6 }}>
            <InverterCard inverter={inv} isLoading={isLoading && !data} />
          </Grid>
        ))}

        {/* Ohmpilots */}
        {ohmpilots.map((ohm) => (
          <Grid key={ohm.componentId} size={{ xs: 12, sm: 6, md: 6 }}>
            <OhmpilotCard ohmpilot={ohm} isLoading={isLoading && !data} />
          </Grid>
        ))}
      </Grid>

      {/* No devices at all (unusual, but possible) */}
      {inverters.length === 0 && ohmpilots.length === 0 && data?.site == null && (
        <Alert severity="warning" sx={{ mt: 2 }}>
          No power flow data available yet. The Solar API scraper may still be initializing.
        </Alert>
      )}
    </Box>
  );
}

export default SolarApiPage;
