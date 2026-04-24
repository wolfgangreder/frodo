import React, { useState, useCallback, useRef } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import UploadIcon from '@mui/icons-material/Upload';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import { useQuery } from '@tanstack/react-query';
import { PageHeader } from '../components/common';
import { deviceApi, metricsApi, systemApi, grafanaService } from '../services';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Export all device configurations (including their metrics config) as a
 * single JSON file that can be re-imported later.
 */
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

/**
 * Import a config file. Returns { imported: number, errors: string[] }.
 */
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

/** Application info + version card */
function AppInfoSection() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['system', 'info'],
    queryFn: systemApi.getInfo,
    staleTime: 60_000,
    retry: false,
  });

  return (
    <Box>
      <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
        Application Info
      </Typography>
      {isLoading && <CircularProgress size={20} />}
      {error && (
        <Typography variant="body2" color="error">
          Failed to load application info
        </Typography>
      )}
      {data && (
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText primary="Name" secondary={data.name} />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText primary="Version" secondary={data.version} />
          </ListItem>
          {data.description && (
            <ListItem disableGutters>
              <ListItemText primary="Description" secondary={data.description} />
            </ListItem>
          )}
        </List>
      )}
    </Box>
  );
}

/** Health status card */
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
    <Box>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ color: 'primary.main' }}>
          Health Status
        </Typography>
        {overall === 'UP' && (
          <Chip icon={<CheckCircleOutlineIcon />} label="UP" color="success" size="small" />
        )}
        {overall === 'DOWN' && (
          <Chip icon={<ErrorOutlineIcon />} label="DOWN" color="error" size="small" />
        )}
        <Button
          size="small"
          variant="outlined"
          onClick={() => refetch()}
          disabled={isFetching}
          startIcon={isFetching ? <CircularProgress size={14} /> : null}
        >
          {isFetching ? 'Checking…' : 'Refresh'}
        </Button>
      </Stack>

      {isLoading && <CircularProgress size={20} />}
      {error && (
        <Typography variant="body2" color="error">
          Failed to reach health endpoint
        </Typography>
      )}
      {checks.length > 0 && (
        <List dense disablePadding>
          {checks.map((check) => (
            <ListItem key={check.name} disableGutters>
              <ListItemText
                primary={check.name}
                secondary={check.data ? JSON.stringify(check.data) : undefined}
              />
              <Chip
                label={check.status}
                color={check.status === 'UP' ? 'success' : 'error'}
                size="small"
                variant="outlined"
              />
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  );
}

/** Modbus connection pool stats */
function ConnectionPoolSection() {
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['system', 'poolStatus'],
    queryFn: systemApi.getPoolStatus,
    refetchInterval: 15_000,
    retry: false,
  });

  const stateColor = {
    CONNECTED: 'success',
    FAILED: 'error',
    CONNECTING: 'warning',
    DISCONNECTED: 'default',
  };

  return (
    <Box>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ color: 'primary.main' }}>
          Connection Pool
        </Typography>
        {data && (
          <Chip
            icon={<NetworkCheckIcon />}
            label={data.connectionState}
            color={stateColor[data.connectionState] || 'default'}
            size="small"
          />
        )}
        <Button
          size="small"
          variant="outlined"
          onClick={() => refetch()}
          disabled={isFetching}
          startIcon={isFetching ? <CircularProgress size={14} /> : null}
        >
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </Stack>

      {isLoading && <CircularProgress size={20} />}
      {error && (
        <Typography variant="body2" color="error">
          Failed to load pool status
        </Typography>
      )}
      {data && (
        <List dense disablePadding>
          <ListItem disableGutters>
            <ListItemText
              primary="Active Connections"
              secondary={data.activeConnections}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Pending Requests"
              secondary={data.pendingRequests}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Total Requests"
              secondary={data.totalRequests.toLocaleString()}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Failed Requests"
              secondary={data.failedRequests.toLocaleString()}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Last Successful Request"
              secondary={data.lastSuccessTime
                ? new Date(data.lastSuccessTime).toLocaleString()
                : 'Never'}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Active Scraping Timers"
              secondary={data.activeScrapingTimers}
            />
          </ListItem>
          <ListItem disableGutters>
            <ListItemText
              primary="Pool Healthy"
              secondary={data.healthy ? 'Yes' : 'No'}
            />
          </ListItem>
        </List>
      )}
    </Box>
  );
}

/** Config export / import section */
function ExportImportSection() {
  const fileRef = useRef(null);
  const [exporting, setExporting] = useState(false);
  const [exportResult, setExportResult] = useState(null); // null | 'ok' | 'error'
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null); // null | { imported, errors } | 'error'
  const [importError, setImportError] = useState('');

  const handleExport = useCallback(async () => {
    setExporting(true);
    setExportResult(null);
    try {
      await exportConfig();
      setExportResult('ok');
    } catch (err) {
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
    <Box>
      <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
        Configuration Export / Import
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Export all device configurations (including metrics settings) to a JSON file for backup or
        migration. Import to restore devices on a fresh installation.
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <Button
          variant="outlined"
          startIcon={exporting ? <CircularProgress size={16} /> : <DownloadIcon />}
          onClick={handleExport}
          disabled={exporting}
        >
          {exporting ? 'Exporting…' : 'Export Config'}
        </Button>

        <Button
          variant="outlined"
          startIcon={importing ? <CircularProgress size={16} /> : <UploadIcon />}
          onClick={() => fileRef.current?.click()}
          disabled={importing}
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
      </Stack>

      {exportResult === 'ok' && (
        <Alert severity="success" sx={{ mt: 2 }} onClose={() => setExportResult(null)}>
          Configuration exported successfully.
        </Alert>
      )}
      {exportResult === 'error' && (
        <Alert severity="error" sx={{ mt: 2 }} onClose={() => setExportResult(null)}>
          Export failed. Check the console for details.
        </Alert>
      )}

      {importResult && importResult !== 'error' && (
        <Alert
          severity={importResult.errors.length === 0 ? 'success' : 'warning'}
          sx={{ mt: 2 }}
          onClose={() => setImportResult(null)}
        >
          Imported {importResult.imported} device(s).
          {importResult.errors.length > 0 && (
            <Box component="ul" sx={{ mt: 0.5, pl: 2, mb: 0 }}>
              {importResult.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </Box>
          )}
        </Alert>
      )}
      {importResult === 'error' && (
        <Alert severity="error" sx={{ mt: 2 }} onClose={() => setImportResult(null)}>
          Import failed: {importError}
        </Alert>
      )}
    </Box>
  );
}

/** Grafana URL configuration */
function GrafanaSettingsSection() {
  const [url, setUrl] = useState(() => grafanaService.getBaseUrl());
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(url);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null); // null | 'ok' | 'error'

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
    <Box>
      <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
        Grafana Integration
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Configure the Grafana server URL used to embed dashboard panels. The URL is stored in
        browser localStorage.
      </Typography>

      {editing ? (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems="flex-start">
          <TextField
            size="small"
            fullWidth
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="http://localhost:3000"
            inputProps={{ style: { fontFamily: 'monospace' } }}
          />
          <Stack direction="row" spacing={1}>
            <Button variant="contained" size="small" onClick={handleSave}>
              Save
            </Button>
            <Button
              variant="outlined"
              size="small"
              onClick={() => {
                setDraft(url);
                setEditing(false);
              }}
            >
              Cancel
            </Button>
          </Stack>
        </Stack>
      ) : (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center">
          <Typography
            variant="body2"
            sx={{ fontFamily: 'monospace', color: 'text.primary', flex: 1 }}
          >
            {url}
          </Typography>

          {testResult === 'ok' && (
            <Chip
              icon={<CheckCircleOutlineIcon />}
              label="Reachable"
              color="success"
              size="small"
              variant="outlined"
            />
          )}
          {testResult === 'error' && (
            <Chip
              icon={<ErrorOutlineIcon />}
              label="Unreachable"
              color="error"
              size="small"
              variant="outlined"
            />
          )}

          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant="outlined"
              onClick={handleTest}
              disabled={testing}
              startIcon={testing ? <CircularProgress size={14} /> : null}
            >
              {testing ? 'Testing…' : 'Test'}
            </Button>
            <Button size="small" onClick={() => setEditing(true)}>
              Change
            </Button>
          </Stack>
        </Stack>
      )}

      {testResult === 'error' && !editing && (
        <Alert severity="warning" sx={{ mt: 2 }}>
          Cannot reach Grafana at <strong>{url}</strong>. Ensure it is running and anonymous access
          is enabled.
        </Alert>
      )}
    </Box>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

function SettingsPage() {
  return (
    <Box>
      <PageHeader
        title="Settings"
        subtitle="Application configuration and system status"
      />

      <Stack spacing={3}>
        {/* App info + Health side by side on larger screens */}
        <Card>
          <CardContent>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={3}
              divider={<Divider orientation="vertical" flexItem />}
            >
              <Box sx={{ flex: 1 }}>
                <AppInfoSection />
              </Box>
              <Box sx={{ flex: 1 }}>
                <HealthSection />
              </Box>
            </Stack>
          </CardContent>
        </Card>

        {/* Connection Pool Stats */}
        <Card>
          <CardContent>
            <ConnectionPoolSection />
          </CardContent>
        </Card>

        {/* Export / Import */}
        <Card>
          <CardContent>
            <ExportImportSection />
          </CardContent>
        </Card>

        {/* Grafana */}
        <Card>
          <CardContent>
            <GrafanaSettingsSection />
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
}

export default SettingsPage;
