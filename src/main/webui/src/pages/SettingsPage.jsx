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

import React, { useState, useCallback, useRef } from 'react';
import {
  Alert,
  AlertActionCloseButton,
  Button,
  Card,
  CardBody,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Label,
  Spinner,
  TextInput,
  Title,
} from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  NetworkIcon,
  DownloadIcon,
  UploadIcon,
} from '@patternfly/react-icons';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/common';
import { deviceApi, metricsApi, systemApi, grafanaService } from '../services';
import { formatForDisplay } from '../utils/timeZone';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #73bcf7)',
  success: 'var(--pf-t--global--icon--color--status--success--default, #5ba352)',
  danger: 'var(--pf-t--global--icon--color--status--danger--default, #c9190b)',
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function exportConfig() {
  const devices = await deviceApi.getAll();

  const devicesWithMetrics = await Promise.all(
    devices.map(async (device) => {
      let metricsConfig = null;
      try {
        const cfg = await metricsApi.getConfig(device.id);
        if (cfg && cfg.enabled) {
          metricsConfig = {
            scrapeIntervalSeconds: cfg.scrapeIntervalSeconds,
            enabled: cfg.enabled,
            storeToDatabase: cfg.storeToDatabase,
            retentionDays: cfg.retentionDays,
            parameters: cfg.parameters,
          };
        }
      } catch {
        // metrics config may not exist for all devices
      }
      return {
        name: device.name,
        host: device.host,
        port: device.port,
        unitId: device.unitId,
        enabled: device.enabled,
        description: device.description ?? null,
        metricsConfig,
      };
    })
  );

  const config = {
    version: '1.1',
    exportDate: new Date().toISOString(),
    devices: devicesWithMetrics,
  };

  const blob = new Blob([JSON.stringify(config, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `frodo-config-${new Date().toISOString().split('T')[0]}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

async function importConfig(file) {
  const text = await file.text();
  let config;
  try {
    config = JSON.parse(text);
  } catch {
    throw new Error('Invalid JSON file');
  }

  if (!config.version || !Array.isArray(config.devices)) {
    throw new Error('Invalid configuration file format (missing version or devices array)');
  }

  const errors = [];
  let imported = 0;

  for (const device of config.devices) {
    try {
      const created = await deviceApi.create({
        name: device.name,
        host: device.host,
        port: device.port,
        unitId: device.unitId,
        enabled: device.enabled ?? true,
        description: device.description ?? '',
      });

      if (device.metricsConfig) {
        try {
          await metricsApi.updateConfig(created.id, device.metricsConfig);
        } catch {
          errors.push(`${device.name}: metrics config not applied`);
        }
      }

      imported++;
    } catch (err) {
      errors.push(`${device.name}: ${err.message || 'failed to create'}`);
    }
  }

  return { imported, errors };
}

// ---------------------------------------------------------------------------
// Sub-sections
// ---------------------------------------------------------------------------

function HealthSection() {
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['system', 'health'],
    queryFn: systemApi.getHealth,
    staleTime: 30_000,
    retry: false,
  });

  const overall = data?.status;
  const checks = data?.checks ?? [];

  return (
    <div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
        <Title headingLevel="h3" size="md" style={{ color: C.primary }}>
          Health Status
        </Title>
        {overall === 'UP' && (
          <Label color="green" icon={<CheckCircleIcon />}>UP</Label>
        )}
        {overall === 'DOWN' && (
          <Label color="red" icon={<ExclamationCircleIcon />}>DOWN</Label>
        )}
        <Button
          size="sm"
          variant="secondary"
          onClick={() => refetch()}
          isDisabled={isFetching}
          icon={isFetching ? <Spinner size="sm" /> : null}
        >
          {isFetching ? 'Checking…' : 'Refresh'}
        </Button>
      </div>

      {isLoading && <Spinner size="sm" />}
      {error && (
        <span style={{ fontSize: '0.875rem', color: C.danger }}>
          Failed to reach health endpoint
        </span>
      )}
      {checks.length > 0 && (
        <DescriptionList isCompact>
          {checks.map((check) => (
            <DescriptionListGroup key={check.name}>
              <DescriptionListTerm>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  {check.name}
                  <Label
                    color={check.status === 'UP' ? 'green' : 'red'}
                    variant="outline"
                  >
                    {check.status}
                  </Label>
                </div>
              </DescriptionListTerm>
              {check.data && (
                <DescriptionListDescription>
                  {JSON.stringify(check.data)}
                </DescriptionListDescription>
              )}
            </DescriptionListGroup>
          ))}
        </DescriptionList>
      )}
    </div>
  );
}

function ConnectionPoolSection() {
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['system', 'poolStatus'],
    queryFn: systemApi.getPoolStatus,
    refetchInterval: 15_000,
    retry: false,
  });

  const stateColor = {
    CONNECTED: 'green',
    FAILED: 'red',
    CONNECTING: 'orange',
    DISCONNECTED: 'grey',
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
        <Title headingLevel="h3" size="md" style={{ color: C.primary }}>
          Connection Pool
        </Title>
        {data && (
          <Label
            color={stateColor[data.connectionState] || 'grey'}
            icon={<NetworkIcon />}
          >
            {data.connectionState}
          </Label>
        )}
        <Button
          size="sm"
          variant="secondary"
          onClick={() => refetch()}
          isDisabled={isFetching}
          icon={isFetching ? <Spinner size="sm" /> : null}
        >
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      {isLoading && <Spinner size="sm" />}
      {error && (
        <span style={{ fontSize: '0.875rem', color: C.danger }}>
          Failed to load pool status
        </span>
      )}
      {data && (
        <DescriptionList isCompact columnModifier={{ default: '2Col' }}>
          <DescriptionListGroup>
            <DescriptionListTerm>Active Connections</DescriptionListTerm>
            <DescriptionListDescription>{data.activeConnections}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Pending Requests</DescriptionListTerm>
            <DescriptionListDescription>{data.pendingRequests}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Total Requests</DescriptionListTerm>
            <DescriptionListDescription>{data.totalRequests.toLocaleString()}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Failed Requests</DescriptionListTerm>
            <DescriptionListDescription>{data.failedRequests.toLocaleString()}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Last Successful Request</DescriptionListTerm>
            <DescriptionListDescription>
              {data.lastSuccessTime ? formatForDisplay(data.lastSuccessTime) : 'Never'}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Active Scraping Timers</DescriptionListTerm>
            <DescriptionListDescription>{data.activeScrapingTimers}</DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Pool Healthy</DescriptionListTerm>
            <DescriptionListDescription>{data.healthy ? 'Yes' : 'No'}</DescriptionListDescription>
          </DescriptionListGroup>
        </DescriptionList>
      )}
    </div>
  );
}

function ExportImportSection() {
  const fileRef = useRef(null);
  const [exporting, setExporting] = useState(false);
  const [exportResult, setExportResult] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState('');

  const handleExport = useCallback(async () => {
    setExporting(true);
    setExportResult(null);
    try {
      await exportConfig();
      setExportResult('ok');
    } catch {
      setExportResult('error');
    } finally {
      setExporting(false);
    }
  }, []);

  const handleFileChange = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';

    setImporting(true);
    setImportResult(null);
    setImportError('');
    try {
      const result = await importConfig(file);
      setImportResult(result);
    } catch (err) {
      setImportResult('error');
      setImportError(err.message);
    } finally {
      setImporting(false);
    }
  }, []);

  return (
    <div>
      <Title headingLevel="h3" size="md" style={{ color: C.primary, marginBottom: 8 }}>
        Configuration Export / Import
      </Title>
      <p style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: 16 }}>
        Export all device configurations (including metrics settings) to a JSON file for backup or
        migration. Import to restore devices on a fresh installation.
      </p>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        <Button
          variant="secondary"
          icon={exporting ? <Spinner size="sm" /> : <DownloadIcon />}
          onClick={handleExport}
          isDisabled={exporting}
        >
          {exporting ? 'Exporting…' : 'Export Config'}
        </Button>

        <Button
          variant="secondary"
          icon={importing ? <Spinner size="sm" /> : <UploadIcon />}
          onClick={() => fileRef.current?.click()}
          isDisabled={importing}
        >
          {importing ? 'Importing…' : 'Import Config'}
        </Button>
        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </div>

      {exportResult === 'ok' && (
        <Alert
          variant="success"
          title="Configuration exported successfully."
          isInline
          style={{ marginTop: 16 }}
          actionClose={<AlertActionCloseButton onClose={() => setExportResult(null)} />}
        />
      )}
      {exportResult === 'error' && (
        <Alert
          variant="danger"
          title="Export failed. Check the console for details."
          isInline
          style={{ marginTop: 16 }}
          actionClose={<AlertActionCloseButton onClose={() => setExportResult(null)} />}
        />
      )}

      {importResult && importResult !== 'error' && (
        <Alert
          variant={importResult.errors.length === 0 ? 'success' : 'warning'}
          title={`Imported ${importResult.imported} device(s).`}
          isInline
          style={{ marginTop: 16 }}
          actionClose={<AlertActionCloseButton onClose={() => setImportResult(null)} />}
        >
          {importResult.errors.length > 0 && (
            <ul style={{ marginTop: 4, paddingLeft: 16, marginBottom: 0 }}>
              {importResult.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          )}
        </Alert>
      )}
      {importResult === 'error' && (
        <Alert
          variant="danger"
          title={`Import failed: ${importError}`}
          isInline
          style={{ marginTop: 16 }}
          actionClose={<AlertActionCloseButton onClose={() => setImportResult(null)} />}
        />
      )}
    </div>
  );
}

function GrafanaSettingsSection() {
  const [url, setUrl] = useState(() => grafanaService.getBaseUrl());
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(url);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);

  const handleSave = () => {
    grafanaService.setBaseUrl(draft);
    setUrl(grafanaService.getBaseUrl());
    setEditing(false);
    setTestResult(null);
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    const ok = await grafanaService.testConnection();
    setTestResult(ok ? 'ok' : 'error');
    setTesting(false);
  };

  return (
    <div>
      <Title headingLevel="h3" size="md" style={{ color: C.primary, marginBottom: 8 }}>
        Grafana Integration
      </Title>
      <p style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: 16 }}>
        Configure the Grafana server URL used to embed dashboard panels. The URL is stored in
        browser localStorage.
      </p>

      {editing ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'flex-start' }}>
          <TextInput
            value={draft}
            onChange={(_event, value) => setDraft(value)}
            placeholder="http://localhost:3000"
            style={{ fontFamily: 'monospace', flex: 1, minWidth: 200 }}
            aria-label="Grafana URL"
          />
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="primary" size="sm" onClick={handleSave}>Save</Button>
            <Button variant="secondary" size="sm" onClick={() => { setDraft(url); setEditing(false); }}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
          <span style={{ fontFamily: 'monospace', fontSize: '0.875rem', flex: 1 }}>{url}</span>

          {testResult === 'ok' && (
            <Label color="green" icon={<CheckCircleIcon />} variant="outline">Reachable</Label>
          )}
          {testResult === 'error' && (
            <Label color="red" icon={<ExclamationCircleIcon />} variant="outline">Unreachable</Label>
          )}

          <div style={{ display: 'flex', gap: 8 }}>
            <Button
              size="sm"
              variant="secondary"
              onClick={handleTest}
              isDisabled={testing}
              icon={testing ? <Spinner size="sm" /> : null}
            >
              {testing ? 'Testing…' : 'Test'}
            </Button>
            <Button size="sm" variant="link" onClick={() => setEditing(true)}>
              Change
            </Button>
          </div>
        </div>
      )}

      {testResult === 'error' && !editing && (
        <Alert
          variant="warning"
          title={`Cannot reach Grafana at ${url}. Ensure it is running and anonymous access is enabled.`}
          isInline
          style={{ marginTop: 16 }}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

function SettingsPage() {
  return (
    <div>
      <PageHeader
        title="Settings"
        subtitle="Application configuration and system status"
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Card>
          <CardBody>
            <HealthSection />
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <ConnectionPoolSection />
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <ExportImportSection />
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <GrafanaSettingsSection />
          </CardBody>
        </Card>
      </div>
    </div>
  );
}

export default SettingsPage;
