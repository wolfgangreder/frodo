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

import React, { useState, useCallback } from 'react';
import {
  Alert,
  Button,
  Card,
  CardBody,
  Divider,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Grid,
  GridItem,
  Label,
  Spinner,
  TextInput,
  Title,
  Tooltip,
} from '@patternfly/react-core';
import {
  SyncAltIcon,
  ExternalLinkAltIcon,
  CogIcon,
  CheckCircleIcon,
  ExclamationCircleIcon,
} from '@patternfly/react-icons';
import { PageHeader } from '../components/common';
import { GrafanaPanel } from '../components/grafana';
import { grafanaService } from '../services';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #73bcf7)',
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
};

/**
 * Predefined time range options shown in the toolbar.
 */
const TIME_RANGES = [
  { label: 'Last 15 min', value: 'now-15m' },
  { label: 'Last 1 hour', value: 'now-1h' },
  { label: 'Last 3 hours', value: 'now-3h' },
  { label: 'Last 6 hours', value: 'now-6h' },
  { label: 'Last 12 hours', value: 'now-12h' },
  { label: 'Last 24 hours', value: 'now-24h' },
  { label: 'Last 7 days', value: 'now-7d' },
];

/**
 * Grafana connection status banner.
 */
function ConnectionBanner({ baseUrl, onChangeUrl }) {
  const [status, setStatus] = useState(null); // null | 'checking' | 'ok' | 'error'

  const handleTest = useCallback(async () => {
    setStatus('checking');
    const ok = await grafanaService.testConnection();
    setStatus(ok ? 'ok' : 'error');
  }, []);

  return (
    <Card style={{ marginBottom: 24 }}>
      <CardBody>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
          <span style={{ fontSize: '0.875rem', color: C.subtle, flexShrink: 0 }}>
            Grafana URL:
          </span>
          <span style={{ fontFamily: 'monospace', fontSize: '0.875rem', flex: 1, wordBreak: 'break-all' }}>
            {baseUrl}
          </span>

          {status === 'ok' && (
            <Label color="green" icon={<CheckCircleIcon />} variant="outline">Reachable</Label>
          )}
          {status === 'error' && (
            <Label color="red" icon={<ExclamationCircleIcon />} variant="outline">Unreachable</Label>
          )}

          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Button
              size="sm"
              variant="secondary"
              onClick={handleTest}
              isDisabled={status === 'checking'}
              icon={status === 'checking' ? <Spinner size="sm" /> : null}
            >
              {status === 'checking' ? 'Testing…' : 'Test Connection'}
            </Button>

            <Tooltip content="Open Grafana in new tab">
              <Button
                variant="plain"
                component="a"
                href={baseUrl}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Open Grafana in new tab"
              >
                <ExternalLinkAltIcon />
              </Button>
            </Tooltip>

            <Tooltip content="Change Grafana URL (go to Settings)">
              <Button
                variant="plain"
                onClick={onChangeUrl}
                aria-label="Change Grafana URL"
              >
                <CogIcon />
              </Button>
            </Tooltip>
          </div>
        </div>

        {status === 'error' && (
          <Alert variant="warning" isInline style={{ marginTop: 16 }} title="">
            Cannot reach Grafana at <strong>{baseUrl}</strong>. Make sure Grafana is running and
            has <code>GF_SECURITY_ALLOW_EMBEDDING=true</code> and{' '}
            <code>GF_AUTH_ANONYMOUS_ENABLED=true</code> set.
          </Alert>
        )}
      </CardBody>
    </Card>
  );
}

/**
 * Inline URL editor shown when the user clicks the settings icon.
 */
function UrlEditor({ initial, onSave, onCancel }) {
  const [value, setValue] = useState(initial);

  return (
    <Card style={{ marginBottom: 24, border: `1px solid ${C.primary}` }}>
      <CardBody>
        <Title headingLevel="h3" size="md" style={{ marginBottom: 8 }}>
          Grafana Base URL
        </Title>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'flex-start' }}>
          <TextInput
            value={value}
            onChange={(_event, val) => setValue(val)}
            placeholder="http://localhost:3000"
            style={{ fontFamily: 'monospace', flex: 1, minWidth: 200 }}
            aria-label="Grafana base URL"
          />
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="primary" size="sm" onClick={() => onSave(value)}>Save</Button>
            <Button variant="secondary" size="sm" onClick={onCancel}>Cancel</Button>
          </div>
        </div>
        <p style={{ fontSize: '0.75rem', color: C.subtle, marginTop: 8 }}>
          Saved in browser localStorage. Dashboard UIDs must be configured in Grafana.
        </p>
      </CardBody>
    </Card>
  );
}

/** Small inline UID input. */
function DashboardUidEditor({ initial, onSave, onCancel }) {
  const [value, setValue] = useState(initial);
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'flex-start' }}>
      <TextInput
        value={value}
        onChange={(_event, val) => setValue(val)}
        placeholder="e.g. abc123XYZ"
        style={{ fontFamily: 'monospace', flex: 1, minWidth: 200 }}
        aria-label="Dashboard UID"
      />
      <div style={{ display: 'flex', gap: 8 }}>
        <Button variant="primary" size="sm" onClick={() => onSave(value)}>Save</Button>
        {initial && (
          <Button variant="secondary" size="sm" onClick={onCancel}>Cancel</Button>
        )}
      </div>
    </div>
  );
}

const DASHBOARD_UID_KEY = 'frodo.grafana.dashboardUid';

const PANELS = [
  { id: 1, title: 'Power Generation', description: 'AC power output over time' },
  { id: 2, title: 'Battery State of Charge', description: 'Battery SoC (%) over time' },
  { id: 3, title: 'Grid Import / Export', description: 'Grid power flow over time' },
  { id: 4, title: 'System Overview', description: 'Key metrics at a glance' },
];

function GrafanaPage() {
  const [baseUrl, setBaseUrl] = useState(() => grafanaService.getBaseUrl());
  const [dashboardUid, setDashboardUid] = useState(
    () => localStorage.getItem(DASHBOARD_UID_KEY) || ''
  );
  const [timeRange, setTimeRange] = useState('now-1h');
  const [editingUrl, setEditingUrl] = useState(false);
  const [editingDashUid, setEditingDashUid] = useState(!localStorage.getItem(DASHBOARD_UID_KEY));
  const [refreshKey, setRefreshKey] = useState(0);

  const handleSaveUrl = (url) => {
    grafanaService.setBaseUrl(url);
    setBaseUrl(grafanaService.getBaseUrl());
    setEditingUrl(false);
    setRefreshKey((k) => k + 1);
  };

  const handleSaveDashUid = (uid) => {
    localStorage.setItem(DASHBOARD_UID_KEY, uid.trim());
    setDashboardUid(uid.trim());
    setEditingDashUid(false);
    setRefreshKey((k) => k + 1);
  };

  const handleRefresh = () => setRefreshKey((k) => k + 1);

  const timeRangeActions = (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
      <FormGroup fieldId="grafana-time-range" style={{ margin: 0 }}>
        <FormSelect
          id="grafana-time-range"
          value={timeRange}
          onChange={(_event, value) => {
            setTimeRange(value);
            setRefreshKey((k) => k + 1);
          }}
          aria-label="Time range"
          style={{ minWidth: 150 }}
        >
          {TIME_RANGES.map((r) => (
            <FormSelectOption key={r.value} value={r.value} label={r.label} />
          ))}
        </FormSelect>
      </FormGroup>
      <Tooltip content="Reload panels">
        <Button variant="plain" onClick={handleRefresh} aria-label="Reload Grafana panels">
          <SyncAltIcon />
        </Button>
      </Tooltip>
    </div>
  );

  return (
    <div>
      <PageHeader
        title="Grafana Dashboards"
        subtitle="Embedded metrics visualizations from Grafana"
        actions={timeRangeActions}
      />

      {editingUrl ? (
        <UrlEditor initial={baseUrl} onSave={handleSaveUrl} onCancel={() => setEditingUrl(false)} />
      ) : (
        <ConnectionBanner baseUrl={baseUrl} onChangeUrl={() => setEditingUrl(true)} />
      )}

      {/* Dashboard UID setup */}
      {editingDashUid ? (
        <Card style={{ marginBottom: 24, border: '1px solid var(--pf-t--global--color--nonstatus--purple--default, #cbc1ff)' }}>
          <CardBody>
            <Title headingLevel="h3" size="md" style={{ marginBottom: 8 }}>
              Grafana Dashboard UID
            </Title>
            <p style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: 8 }}>
              Enter the UID of your Grafana dashboard. You can find it in the dashboard URL:
              <code> /d/&lt;UID&gt;/dashboard-name</code>.
            </p>
            <DashboardUidEditor
              initial={dashboardUid}
              onSave={handleSaveDashUid}
              onCancel={() => setEditingDashUid(false)}
            />
          </CardBody>
        </Card>
      ) : dashboardUid ? (
        <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: '0.875rem', color: C.subtle }}>Dashboard UID:</span>
          <Label color="grey" variant="outline">{dashboardUid}</Label>
          <Button size="sm" variant="link" onClick={() => setEditingDashUid(true)}>
            Change
          </Button>
        </div>
      ) : null}

      {!dashboardUid && !editingDashUid && (
        <Alert
          variant="info"
          title="No Grafana dashboard UID configured. Set it above to show panels."
          isInline
          style={{ marginBottom: 24 }}
          actionLinks={
            <Button variant="link" isInline onClick={() => setEditingDashUid(true)}>
              Configure
            </Button>
          }
        />
      )}

      {dashboardUid && (
        <Grid hasGutter>
          {PANELS.map((panel) => (
            <GridItem key={panel.id} span={12} md={6}>
              <GrafanaPanel
                key={`${refreshKey}-${panel.id}`}
                title={panel.title}
                src={grafanaService.buildPanelUrl({
                  dashboardUid,
                  panelId: panel.id,
                  from: timeRange,
                  to: 'now',
                  refresh: '30s',
                  theme: 'dark',
                })}
                externalUrl={grafanaService.buildDashboardUrl(dashboardUid, {
                  from: timeRange,
                  to: 'now',
                })}
              />
            </GridItem>
          ))}
        </Grid>
      )}

      {!dashboardUid && (
        <Card>
          <CardBody>
            <Title headingLevel="h3" size="lg" style={{ color: C.primary, marginBottom: 8 }}>
              Getting Started with Grafana
            </Title>
            <Divider style={{ marginBottom: 16 }} />

            <p style={{ fontSize: '0.875rem', marginBottom: 8 }}>
              1. Start Grafana (see <code>docker-compose.yml</code>):
            </p>
            <pre style={{
              background: 'var(--pf-t--global--background--color--secondary--default, #212427)',
              padding: '12px',
              borderRadius: 4,
              fontSize: '0.75rem',
              overflowX: 'auto',
              marginBottom: 16,
            }}>
              {`services:
  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment:
      - GF_SECURITY_ALLOW_EMBEDDING=true
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer`}
            </pre>

            <p style={{ fontSize: '0.875rem', marginBottom: 8 }}>
              2. Add Prometheus as a data source in Grafana pointing to{' '}
              <code>http://host.docker.internal:8080/q/metrics</code>.
            </p>

            <p style={{ fontSize: '0.875rem', marginTop: 8 }}>
              3. Create a dashboard with panels for power, battery, grid, and system metrics, then
              copy the dashboard UID from the URL and enter it above.
            </p>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

export default GrafanaPage;
