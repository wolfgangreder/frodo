import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Chip,
  MenuItem,
  TextField,
  Alert,
  Button,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import { DeviceDashboard } from '../components/dashboard';
import { useDeviceList } from '../hooks';
import { systemApi } from '../services';

/**
 * Dashboard page - main overview of PV system status
 *
 * Shows a device selector (when multiple devices) and real-time
 * SunSpec dashboard cards for the selected device.
 */
function DashboardPage() {
  const navigate = useNavigate();

  // App info
  const {
    data: appInfo,
    isLoading: isAppInfoLoading,
  } = useQuery({
    queryKey: ['appInfo'],
    queryFn: systemApi.getInfo,
  });

  // Device list
  const {
    data: devices,
    isLoading: isDevicesLoading,
    isError: isDevicesError,
    error: devicesError,
    refetch: refetchDevices,
  } = useDeviceList();

  // Selected device ID (stored in local state, persisted in sessionStorage)
  const [selectedDeviceId, setSelectedDeviceId] = useState(() => {
    const stored = sessionStorage.getItem('dashboard.selectedDeviceId');
    return stored ? Number(stored) : null;
  });

  // Auto-select first enabled device when devices load
  useEffect(() => {
    if (devices && devices.length > 0 && selectedDeviceId == null) {
      const enabledDevice = devices.find((d) => d.enabled) || devices[0];
      setSelectedDeviceId(enabledDevice.id);
    }
  }, [devices, selectedDeviceId]);

  // Persist selection
  useEffect(() => {
    if (selectedDeviceId != null) {
      sessionStorage.setItem('dashboard.selectedDeviceId', String(selectedDeviceId));
    }
  }, [selectedDeviceId]);

  const selectedDevice = devices?.find((d) => d.id === selectedDeviceId) || null;

  const isLoading = isDevicesLoading || isAppInfoLoading;

  if (isLoading) {
    return <LoadingSpinner message="Loading dashboard..." fullPage />;
  }

  if (isDevicesError) {
    return (
      <ErrorDisplay
        title="Failed to load dashboard"
        message={devicesError?.message}
        onRetry={refetchDevices}
        fullPage
      />
    );
  }

  return (
    <Box>
      <PageHeader
        title="Dashboard"
        subtitle="Real-time PV system monitoring"
        actions={
          devices && devices.length > 1 ? (
            <TextField
              select
              size="small"
              label="Device"
              value={selectedDeviceId || ''}
              onChange={(e) => setSelectedDeviceId(Number(e.target.value))}
              sx={{ minWidth: 200 }}
            >
              {devices.map((d) => (
                <MenuItem key={d.id} value={d.id}>
                  {d.name}{!d.enabled ? ' (disabled)' : ''}
                </MenuItem>
              ))}
            </TextField>
          ) : null
        }
      />

      {/* No devices configured */}
      {(!devices || devices.length === 0) ? (
        <Card sx={{ mt: 2 }}>
          <CardContent sx={{ textAlign: 'center', py: 4 }}>
            <Typography variant="h6" color="text.secondary" gutterBottom>
              No Devices Configured
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Add a Modbus device to start monitoring your PV system.
            </Typography>
            <Button variant="contained" onClick={() => navigate('/devices')}>
              Go to Devices
            </Button>
          </CardContent>
        </Card>
      ) : !selectedDevice ? (
        <Alert severity="info" sx={{ mt: 2 }}>
          Select a device to view its dashboard.
        </Alert>
      ) : !selectedDevice.enabled ? (
        <Box>
          <Alert severity="warning" sx={{ mb: 2 }}>
            This device is disabled. Enable it in the device configuration to start monitoring.
          </Alert>
          <DeviceDashboard device={selectedDevice} />
        </Box>
      ) : (
        <DeviceDashboard device={selectedDevice} />
      )}

      {/* App info footer */}
      <Grid container spacing={2} sx={{ mt: 3 }}>
        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="subtitle2" color="primary.main" gutterBottom>
                Application
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Name</Typography>
                  <Typography variant="body2">{appInfo?.name || 'Frodo'}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Version</Typography>
                  <Chip label={appInfo?.version || '0.0.0'} size="small" color="primary" />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="subtitle2" color="primary.main" gutterBottom>
                Quick Links
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                {[
                  { href: '/swagger-ui', label: 'Swagger UI' },
                  { href: '/q/metrics', label: 'Prometheus Metrics' },
                  { href: '/q/health', label: 'Health Check' },
                ].map((link) => (
                  <Typography
                    key={link.href}
                    component="a"
                    href={link.href}
                    target="_blank"
                    rel="noreferrer"
                    variant="body2"
                    sx={{ color: 'secondary.main', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                  >
                    {link.label}
                  </Typography>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}

export default DashboardPage;
