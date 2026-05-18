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
  Card,
  CardBody,
  Grid,
  GridItem,
  Label,
  Skeleton,
} from '@patternfly/react-core';
import { SunIcon, BoltIcon, TintIcon } from '@patternfly/react-icons';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import { useSolarApiStatus } from '../hooks';
import { formatTimeOnly } from '../utils/timeZone';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #73bcf7)',
  success: 'var(--pf-t--global--icon--color--status--success--default, #5ba352)',
  warning: 'var(--pf-t--global--icon--color--status--warning--default, #f0ab00)',
  danger: 'var(--pf-t--global--icon--color--status--danger--default, #c9190b)',
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text--color--disabled, #6a6e73)',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function formatPower(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(2)} kW`;
  return `${num.toFixed(1)} W`;
}

function formatEnergy(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000000) return `${(num / 1000000).toFixed(2)} MWh`;
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(1)} kWh`;
  return `${num.toFixed(0)} Wh`;
}

function formatPercent(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  return `${parseFloat(value).toFixed(1)} %`;
}

function formatTemperature(value) {
  if (value == null || (typeof value === 'number' && isNaN(value))) return '-';
  return `${parseFloat(value).toFixed(1)} °C`;
}

function capitalize(str) {
  if (!str) return '-';
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

/** Map state string to PF Label color */
function stateLabelColor(state) {
  if (!state) return 'grey';
  switch (state.toLowerCase()) {
    case 'normal': return 'green';
    case 'boost': return 'orange';
    case 'fault': return 'red';
    case 'startup': return 'cyan';
    case 'standby': return 'grey';
    default: return 'grey';
  }
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

function MetricRow({ label, value, primary = false }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <span style={{ fontSize: '0.875rem', color: C.subtle }}>{label}</span>
      <span style={{
        fontWeight: primary ? 700 : 500,
        fontFamily: 'monospace',
        fontSize: primary ? '1rem' : '0.875rem',
      }}>
        {value}
      </span>
    </div>
  );
}

function MetricsSkeleton({ count }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {[...Array(count)].map((_, i) => (
        <Skeleton key={i} width="100%" height="24px" />
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Site Power Flow Card
// ---------------------------------------------------------------------------

function SitePowerFlowCard({ site, isLoading }) {
  const pvActive = site?.pvPowerWatts != null && site.pvPowerWatts > 0;

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16 }}>
          <SunIcon style={{ color: pvActive ? C.success : C.disabled }} />
          <span style={{ fontSize: '1.125rem', fontWeight: 600, color: C.primary }}>
            Site Power Flow
          </span>
        </div>

        {isLoading ? (
          <MetricsSkeleton count={6} />
        ) : !site ? (
          <Alert variant="info" title="No site data available" isInline />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <MetricRow label="PV Production" value={formatPower(site.pvPowerWatts)} primary />
            <MetricRow label="Grid" value={formatPower(site.gridPowerWatts)} primary />
            <MetricRow
              label="Load"
              value={formatPower(site.loadPowerWatts != null ? Math.abs(site.loadPowerWatts) : null)}
              primary
            />
            <MetricRow label="Battery" value={formatPower(site.batteryPowerWatts)} />
            <MetricRow label="Autonomy" value={formatPercent(site.autonomyPercent)} />
            <MetricRow label="Self-Consumption" value={formatPercent(site.selfConsumptionPercent)} />
          </div>
        )}
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Per-inverter Card
// ---------------------------------------------------------------------------

function InverterCard({ inverter, isLoading }) {
  const isGenerating = inverter?.powerWatts != null && inverter.powerWatts > 0;

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16 }}>
          <BoltIcon style={{ color: isGenerating ? C.success : C.disabled }} />
          <span style={{ fontSize: '1.125rem', fontWeight: 600, color: C.primary }}>
            Inverter {inverter?.deviceId || '?'}
          </span>
          {inverter?.batteryMode && (
            <Label color="grey" style={{ marginLeft: 'auto' }}>{capitalize(inverter.batteryMode)}</Label>
          )}
        </div>

        {isLoading ? (
          <MetricsSkeleton count={3} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <MetricRow label="AC Power" value={formatPower(inverter?.powerWatts)} primary />
            <MetricRow label="Energy Total" value={formatEnergy(inverter?.energyTotalWattHours)} />
            <MetricRow label="Battery SOC" value={formatPercent(inverter?.batterySOCPercent)} />
          </div>
        )}
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Per-Ohmpilot Card
// ---------------------------------------------------------------------------

function OhmpilotCard({ ohmpilot, isLoading }) {
  const isActive = ohmpilot?.powerWatts != null && ohmpilot.powerWatts > 0;

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16 }}>
          <TintIcon style={{ color: isActive ? C.warning : C.disabled }} />
          <span style={{ fontSize: '1.125rem', fontWeight: 600, color: C.primary }}>
            Ohmpilot {ohmpilot?.componentId || '?'}
          </span>
          {ohmpilot?.state && (
            <Label color={stateLabelColor(ohmpilot.state)} style={{ marginLeft: 'auto' }}>
              {capitalize(ohmpilot.state)}
            </Label>
          )}
        </div>

        {isLoading ? (
          <MetricsSkeleton count={3} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <MetricRow label="Power" value={formatPower(ohmpilot?.powerWatts)} primary />
            <MetricRow label="Temperature" value={formatTemperature(ohmpilot?.temperatureCelsius)} />
          </div>
        )}
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Scraping Status Bar
// ---------------------------------------------------------------------------

function ScrapingStatusBar({ data }) {
  if (!data) return null;

  return (
    <Card style={{ marginBottom: 16 }}>
      <CardBody style={{ paddingTop: 12, paddingBottom: 12 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Label color={data.active ? 'green' : 'grey'}>
              {data.active ? 'Active' : 'Inactive'}
            </Label>
            <span style={{ fontSize: '0.875rem', color: C.subtle }}>
              Interval: {data.scrapeIntervalSeconds}s
            </span>
          </div>
          <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
            <span style={{ fontSize: '0.875rem', color: C.subtle }}>
              Scrapes: {data.scrapeCount.toLocaleString()}
            </span>
            {data.errorCount > 0 && (
              <span style={{ fontSize: '0.875rem', color: C.danger }}>
                Errors: {data.errorCount.toLocaleString()}
              </span>
            )}
            {data.lastScrapeTime && (
              <span style={{ fontSize: '0.875rem', color: C.subtle }}>
                Last: {formatTimeOnly(data.lastScrapeTime)}
              </span>
            )}
          </div>
        </div>
      </CardBody>
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
      <div>
        <PageHeader
          title="Solar API"
          subtitle="Fronius Solar API power flow metrics"
        />
        <Alert variant="info" isInline style={{ marginTop: 16 }} title="">
          Solar API integration is disabled. Set{' '}
          <code>frodo.solar-api.enabled=true</code>{' '}
          in your configuration to enable live power flow metrics from the Fronius inverter.
        </Alert>
      </div>
    );
  }

  const inverters = data?.inverters || [];
  const ohmpilots = data?.ohmpilots || [];

  return (
    <div>
      <PageHeader
        title="Solar API"
        subtitle="Live power flow from Fronius Solar API"
      />

      <ScrapingStatusBar data={data} />

      <Grid hasGutter>
        <GridItem span={12} md={6}>
          <SitePowerFlowCard site={data?.site} isLoading={isLoading && !data} />
        </GridItem>

        {inverters.map((inv) => (
          <GridItem key={inv.deviceId} span={12} sm={6} md={6}>
            <InverterCard inverter={inv} isLoading={isLoading && !data} />
          </GridItem>
        ))}

        {ohmpilots.map((ohm) => (
          <GridItem key={ohm.componentId} span={12} sm={6} md={6}>
            <OhmpilotCard ohmpilot={ohm} isLoading={isLoading && !data} />
          </GridItem>
        ))}
      </Grid>

      {inverters.length === 0 && ohmpilots.length === 0 && data?.site == null && (
        <Alert
          variant="warning"
          title="No power flow data available yet. The Solar API scraper may still be initializing."
          isInline
          style={{ marginTop: 16 }}
        />
      )}
    </div>
  );
}

export default SolarApiPage;
