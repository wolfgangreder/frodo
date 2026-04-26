import React, { useMemo } from 'react';
import {
  Box,
  Grid,
  Typography,
  IconButton,
  Stack,
  Tooltip,
  Chip,
  Alert,
  Button,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import { useQueryClient } from '@tanstack/react-query';
import {
  useSunSpecCommon,
  useSunSpecInverter,
  useSunSpecStorage,
  useSunSpecStatus,
  useSunSpecDiscovery,
  sunspecKeys,
} from '../../hooks/useSunSpec';
import DeviceStatusCard from './DeviceStatusCard';
import PowerMetricsCard from './PowerMetricsCard';
import BatteryStatusCard from './BatteryStatusCard';
import GridStatusCard from './GridStatusCard';
import SitePowerFlowCard from './SitePowerFlowCard';

/**
 * Formats a relative time string from an ISO timestamp
 */
function formatTimeAgo(timestamp) {
  if (!timestamp) return null;
  const now = Date.now();
  const then = new Date(timestamp).getTime();
  const diffSec = Math.floor((now - then) / 1000);
  if (diffSec < 10) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  return `${Math.floor(diffSec / 3600)}h ago`;
}

/**
 * DeviceDashboard - orchestrates all dashboard cards for a single device.
 *
 * Fetches SunSpec data via hooks with auto-refresh and displays in a
 * responsive grid layout. When the device is offline (discovery fails),
 * shows a graceful offline state and retries discovery every 60 s.
 *
 * @param {Object} props
 * @param {Object} props.device - Device entity from useDevice hook
 */
function DeviceDashboard({ device }) {
  const queryClient = useQueryClient();
  const deviceId = device?.id;

  // Discovery — drives all other queries; retries every 60 s when offline
  const discoveryQuery = useSunSpecDiscovery(deviceId);
  const discovery = discoveryQuery.data;
  const isOffline = discoveryQuery.isError;
  const isDiscovering = discoveryQuery.isLoading;

  // Determine model availability from successful discovery
  const hasStorage = useMemo(() => {
    if (!discovery?.models) return false;
    return discovery.models.some((m) => m.modelId === 124);
  }, [discovery]);

  const hasStatus = useMemo(() => {
    if (!discovery?.models) return false;
    return discovery.models.some((m) => m.modelId === 122);
  }, [discovery]);

  const hasControls = useMemo(() => {
    if (!discovery?.models) return false;
    return discovery.models.some((m) => m.modelId === 123);
  }, [discovery]);

  // Model queries — only enabled when discovery has succeeded
  const onlineAndReady = !!deviceId && discoveryQuery.isSuccess;

  const commonQuery = useSunSpecCommon(deviceId, { enabled: onlineAndReady });
  const inverterQuery = useSunSpecInverter(deviceId, { enabled: onlineAndReady });
  const storageQuery = useSunSpecStorage(deviceId, { enabled: onlineAndReady && hasStorage });
  const statusQuery = useSunSpecStatus(deviceId, { enabled: onlineAndReady && hasStatus });

  // Last update time from most recent successful read
  const lastUpdate = inverterQuery.data?.readTime || commonQuery.data?.readTime;

  const handleRefreshAll = () => {
    // Invalidate discovery first — enables model queries if device came back online
    queryClient.invalidateQueries({ queryKey: sunspecKeys.discovery(deviceId) });
    if (onlineAndReady) {
      queryClient.invalidateQueries({ queryKey: sunspecKeys.common(deviceId) });
      queryClient.invalidateQueries({ queryKey: sunspecKeys.inverter(deviceId) });
      if (hasStorage) {
        queryClient.invalidateQueries({ queryKey: sunspecKeys.storage(deviceId) });
      }
      if (hasStatus) {
        queryClient.invalidateQueries({ queryKey: sunspecKeys.status(deviceId) });
      }
    }
  };

  const isAnyFetching =
    discoveryQuery.isFetching ||
    commonQuery.isFetching ||
    inverterQuery.isFetching ||
    storageQuery.isFetching ||
    statusQuery.isFetching;

  return (
    <Box>
      {/* Toolbar */}
      <Stack direction="row" justifyContent="flex-end" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        {lastUpdate && !isOffline && (
          <Chip
            label={`Updated ${formatTimeAgo(lastUpdate)}`}
            size="small"
            variant="outlined"
            color={inverterQuery.isError ? 'error' : 'default'}
          />
        )}
        {isOffline && (
          <Chip
            icon={<WifiOffIcon />}
            label="Offline — retrying in 60 s"
            size="small"
            color="warning"
            variant="outlined"
          />
        )}
        {!isOffline && (
          <Typography variant="caption" color="text.secondary">
            Auto-refresh: 10 s
          </Typography>
        )}
        <Tooltip title="Refresh all data">
          <IconButton
            size="small"
            onClick={handleRefreshAll}
            disabled={isAnyFetching}
            aria-label="Refresh all device data"
            sx={{
              animation: isAnyFetching ? 'spin 1s linear infinite' : 'none',
              '@keyframes spin': { from: { transform: 'rotate(0deg)' }, to: { transform: 'rotate(360deg)' } },
            }}
          >
            <RefreshIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>

      {/* Offline state — show compact layout with device info and notice */}
      {isOffline && (
        <Box>
          <Alert
            severity="warning"
            sx={{ mb: 2 }}
            action={
              <Button
                color="inherit"
                size="small"
                onClick={handleRefreshAll}
                disabled={discoveryQuery.isFetching}
              >
                Retry now
              </Button>
            }
          >
            Modbus connection unavailable — SunSpec data cannot be read.
            Discovery retries automatically every 60 s.
          </Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <DeviceStatusCard
                device={device}
                commonData={null}
                inverterData={null}
                isLoading={false}
                isError
              />
            </Grid>
          </Grid>
        </Box>
      )}

      {/* Online / discovering state — full 4-card grid */}
      {!isOffline && (
        <Grid container spacing={2}>
          {/* Device Status - always shown */}
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <DeviceStatusCard
              device={device}
              commonData={commonQuery.data}
              inverterData={inverterQuery.data}
              isLoading={isDiscovering || commonQuery.isLoading}
              isError={commonQuery.isError && inverterQuery.isError}
            />
          </Grid>

          {/* Power Metrics - always shown */}
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <PowerMetricsCard
              inverterData={inverterQuery.data}
              isLoading={isDiscovering || inverterQuery.isLoading}
              isError={inverterQuery.isError}
            />
          </Grid>

          {/* Battery Status - shown if device has storage */}
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <BatteryStatusCard
              storageData={storageQuery.data}
              isLoading={(storageQuery.isLoading && hasStorage) || isDiscovering}
              isError={storageQuery.isError}
              hasStorage={hasStorage}
            />
          </Grid>

          {/* Site Power Flow - always shown when Solar API is enabled */}
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <SitePowerFlowCard
              deviceId={deviceId}
              statusData={statusQuery.data}
              hasControls={hasControls}
            />
          </Grid>

          {/* Grid Status - shown if device has status model */}
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <GridStatusCard
              deviceId={deviceId}
              statusData={statusQuery.data}
              inverterData={inverterQuery.data}
              isLoading={(statusQuery.isLoading && hasStatus) || isDiscovering}
              isError={statusQuery.isError && hasStatus}
              hasControls={hasControls}
            />
          </Grid>
        </Grid>
      )}
    </Box>
  );
}

export default DeviceDashboard;
