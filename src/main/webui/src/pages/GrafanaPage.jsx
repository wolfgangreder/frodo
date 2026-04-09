import React, { useState, useCallback } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  IconButton,
  MenuItem,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import SettingsIcon from '@mui/icons-material/Settings';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { PageHeader } from '../components/common';
import { GrafanaPanel } from '../components/grafana';
import { grafanaService } from '../services';

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
 * Shows a test button; on click fires a ping to Grafana /api/health.
 */
function ConnectionBanner({ baseUrl, onChangeUrl }) {
  const [status, setStatus] = useState(null); // null | 'checking' | 'ok' | 'error'

  const handleTest = useCallback(async () => {
    setStatus('checking');
    const ok = await grafanaService.testConnection();
    setStatus(ok ? 'ok' : 'error');
  }, []);

  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          alignItems={{ xs: 'stretch', sm: 'center' }}
        >
          <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0 }}>
            Grafana URL:
          </Typography>

          <Typography
            variant="body2"
            sx={{ fontFamily: 'monospace', color: 'text.primary', flex: 1, wordBreak: 'break-all' }}
          >
            {baseUrl}
          </Typography>

          {status === 'ok' && (
            <Chip
              icon={<CheckCircleOutlineIcon />}
              label="Reachable"
              color="success"
              size="small"
              variant="outlined"
            />
          )}
          {status === 'error' && (
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
              disabled={status === 'checking'}
              startIcon={status === 'checking' ? <CircularProgress size={14} /> : null}
            >
              {status === 'checking' ? 'Testing…' : 'Test Connection'}
            </Button>

            <Tooltip title="Open Grafana in new tab">
              <IconButton
                size="small"
                href={baseUrl}
                target="_blank"
                rel="noopener noreferrer"
                component="a"
                aria-label="Open Grafana in new tab"
              >
                <OpenInNewIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Tooltip title="Change Grafana URL (go to Settings)">
              <IconButton size="small" onClick={onChangeUrl} aria-label="Change Grafana URL">
                <SettingsIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        </Stack>

        {status === 'error' && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            Cannot reach Grafana at <strong>{baseUrl}</strong>. Make sure Grafana is running and
            has <code>GF_SECURITY_ALLOW_EMBEDDING=true</code> and{' '}
            <code>GF_AUTH_ANONYMOUS_ENABLED=true</code> set.
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * Inline URL editor shown when the user clicks the settings icon.
 */
function UrlEditor({ initial, onSave, onCancel }) {
  const [value, setValue] = useState(initial);

  return (
    <Card sx={{ mb: 3, border: 1, borderColor: 'primary.main' }}>
      <CardContent>
        <Typography variant="subtitle2" gutterBottom>
          Grafana Base URL
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems="flex-start">
          <TextField
            size="small"
            fullWidth
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="http://localhost:3000"
            inputProps={{ style: { fontFamily: 'monospace' } }}
          />
          <Stack direction="row" spacing={1}>
            <Button variant="contained" size="small" onClick={() => onSave(value)}>
              Save
            </Button>
            <Button variant="outlined" size="small" onClick={onCancel}>
              Cancel
            </Button>
          </Stack>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          Saved in browser localStorage. Dashboard UIDs must be configured in Grafana.
        </Typography>
      </CardContent>
    </Card>
  );
}

/**
 * GrafanaPage — embeds four Grafana panels for PV system monitoring.
 *
 * The panels expect a Grafana dashboard with the UID configured via the
 * Settings page or localStorage key "frodo.grafana.dashboardUid".
 *
 * If no dashboard UID is configured, a setup guide is shown instead.
 */
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
    <>
      <Select
        size="small"
        value={timeRange}
        onChange={(e) => {
          setTimeRange(e.target.value);
          setRefreshKey((k) => k + 1);
        }}
        sx={{ minWidth: 150 }}
      >
        {TIME_RANGES.map((r) => (
          <MenuItem key={r.value} value={r.value}>
            {r.label}
          </MenuItem>
        ))}
      </Select>
      <Tooltip title="Reload panels">
        <IconButton onClick={handleRefresh} aria-label="Reload Grafana panels">
          <RefreshIcon />
        </IconButton>
      </Tooltip>
    </>
  );

  return (
    <Box>
      <PageHeader
        title="Grafana Dashboards"
        subtitle="Embedded metrics visualizations from Grafana"
        actions={timeRangeActions}
      />

      {/* Grafana connection info / URL editor */}
      {editingUrl ? (
        <UrlEditor initial={baseUrl} onSave={handleSaveUrl} onCancel={() => setEditingUrl(false)} />
      ) : (
        <ConnectionBanner baseUrl={baseUrl} onChangeUrl={() => setEditingUrl(true)} />
      )}

      {/* Dashboard UID setup */}
      {editingDashUid ? (
        <Card sx={{ mb: 3, border: 1, borderColor: 'secondary.main' }}>
          <CardContent>
            <Typography variant="subtitle2" gutterBottom>
              Grafana Dashboard UID
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Enter the UID of your Grafana dashboard. You can find it in the dashboard URL:
              <code> /d/&lt;UID&gt;/dashboard-name</code>.
            </Typography>
            <DashboardUidEditor
              initial={dashboardUid}
              onSave={handleSaveDashUid}
              onCancel={() => setEditingDashUid(false)}
            />
          </CardContent>
        </Card>
      ) : dashboardUid ? (
        <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Dashboard UID:
          </Typography>
          <Chip label={dashboardUid} size="small" variant="outlined" />
          <Button size="small" onClick={() => setEditingDashUid(true)}>
            Change
          </Button>
        </Box>
      ) : null}

      {/* No dashboard UID configured */}
      {!dashboardUid && !editingDashUid && (
        <Alert
          severity="info"
          action={
            <Button color="inherit" size="small" onClick={() => setEditingDashUid(true)}>
              Configure
            </Button>
          }
          sx={{ mb: 3 }}
        >
          No Grafana dashboard UID configured. Set it above to show panels.
        </Alert>
      )}

      {/* Panel grid */}
      {dashboardUid && (
        <Grid container spacing={2}>
          {PANELS.map((panel) => (
            <Grid key={panel.id} item xs={12} md={6}>
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
            </Grid>
          ))}
        </Grid>
      )}

      {/* Setup guide shown when no UID yet */}
      {!dashboardUid && (
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
              Getting Started with Grafana
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Typography variant="body2" gutterBottom>
              1. Start Grafana (see <code>docker-compose.yml</code>):
            </Typography>
            <Box
              component="pre"
              sx={{
                bgcolor: 'background.default',
                p: 1.5,
                borderRadius: 1,
                fontSize: '0.75rem',
                overflowX: 'auto',
                mb: 2,
              }}
            >
              {`services:
  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment:
      - GF_SECURITY_ALLOW_EMBEDDING=true
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer`}
            </Box>

            <Typography variant="body2" gutterBottom>
              2. Add Prometheus as a data source in Grafana pointing to{' '}
              <code>http://host.docker.internal:8080/q/metrics</code>.
            </Typography>

            <Typography variant="body2" gutterBottom sx={{ mt: 1 }}>
              3. Create a dashboard with panels for power, battery, grid, and system metrics, then
              copy the dashboard UID from the URL and enter it above.
            </Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  );
}

/** Small inline UID input extracted to keep JSX manageable. */
function DashboardUidEditor({ initial, onSave, onCancel }) {
  const [value, setValue] = useState(initial);
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems="flex-start">
      <TextField
        size="small"
        fullWidth
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="e.g. abc123XYZ"
        inputProps={{ style: { fontFamily: 'monospace' } }}
      />
      <Stack direction="row" spacing={1}>
        <Button variant="contained" size="small" onClick={() => onSave(value)}>
          Save
        </Button>
        {initial && (
          <Button variant="outlined" size="small" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </Stack>
    </Stack>
  );
}

export default GrafanaPage;
