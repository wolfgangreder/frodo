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
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, LoadingSpinner, ErrorDisplay, EmptyState } from '../components/common';
import RouterIcon from '@mui/icons-material/Router';
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
        <EmptyState
          title="No Devices Configured"
          description="Add a Modbus device to start monitoring your PV system."
          icon={<RouterIcon sx={{ fontSize: 48, opacity: 0.5 }} />}
          actionLabel="Go to Devices"
          onAction={() => navigate('/devices')}
        />
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
