import React, { useMemo } from 'react';
import {
  Card,
  CardContent,
  Box,
  Typography,
  Skeleton,
  Alert,
  Stack,
  LinearProgress,
} from '@mui/material';
import BatteryChargingFullIcon from '@mui/icons-material/BatteryChargingFull';
import Battery60Icon from '@mui/icons-material/Battery60';
import Battery20Icon from '@mui/icons-material/Battery20';
import BatteryFullIcon from '@mui/icons-material/BatteryFull';
import BatteryAlertIcon from '@mui/icons-material/BatteryAlert';

/**
 * Returns a battery icon based on charge state percentage
 */
function getBatteryIcon(soc, isCharging) {
  if (soc == null) return <BatteryAlertIcon sx={{ color: 'text.disabled' }} />;
  if (isCharging) return <BatteryChargingFullIcon sx={{ color: 'success.main' }} />;
  if (soc >= 80) return <BatteryFullIcon sx={{ color: 'success.main' }} />;
  if (soc >= 30) return <Battery60Icon sx={{ color: 'warning.main' }} />;
  if (soc >= 10) return <Battery20Icon sx={{ color: 'warning.main' }} />;
  return <BatteryAlertIcon sx={{ color: 'error.main' }} />;
}

/**
 * Maps charge status enum to display label
 */
function getChargeStatus(chaSt) {
  if (chaSt == null) return { label: 'Unknown', color: 'text.disabled' };
  const map = {
    1: { label: 'Off', color: 'text.disabled' },
    2: { label: 'Empty', color: 'error.main' },
    3: { label: 'Discharging', color: 'warning.main' },
    4: { label: 'Charging', color: 'success.main' },
    5: { label: 'Full', color: 'success.main' },
    6: { label: 'Holding', color: 'info.main' },
    7: { label: 'Testing', color: 'info.main' },
  };
  return map[chaSt] || { label: `Status ${chaSt}`, color: 'text.secondary' };
}

/**
 * Returns the progress bar color based on SoC level
 */
function getSocColor(soc) {
  if (soc == null) return 'inherit';
  if (soc >= 60) return 'success';
  if (soc >= 20) return 'warning';
  return 'error';
}

/**
 * BatteryStatusCard - shows battery state of charge, voltage, current, status
 *
 * @param {Object} props
 * @param {Object} props.storageData - SunSpec Storage model (124) response
 * @param {boolean} props.isLoading - Whether data is still loading
 * @param {boolean} props.isError - Whether a fetch error occurred
 * @param {boolean} props.hasStorage - Whether the device has a storage model
 */
function BatteryStatusCard({ storageData, isLoading, isError, hasStorage = true }) {
  const fields = storageData?.fields || {};

  const soc = fields.ChaState != null ? parseFloat(fields.ChaState) : null;
  const batteryV = fields.InBatV != null ? parseFloat(fields.InBatV) : null;
  const chargeStatus = useMemo(() => getChargeStatus(fields.ChaSt), [fields.ChaSt]);
  const isCharging = fields.ChaSt === 4;
  const minReserve = fields.MinRsvPct != null ? parseFloat(fields.MinRsvPct) : null;
  const storageAvailable = fields.StorAval != null ? parseFloat(fields.StorAval) : null;

  if (!hasStorage) {
    return (
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Typography variant="h6" sx={{ color: 'primary.main', mb: 1 }}>
            Battery
          </Typography>
          <Alert severity="info" variant="outlined">
            No battery/storage system detected on this device.
          </Alert>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            {getBatteryIcon(soc, isCharging)}
            <Typography variant="h6" sx={{ color: 'primary.main' }}>
              Battery
            </Typography>
          </Stack>
          <Typography variant="caption" sx={{ color: chargeStatus.color, fontWeight: 600 }}>
            {chargeStatus.label}
          </Typography>
        </Stack>

        {isLoading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            <Skeleton variant="rectangular" height={20} />
            {[...Array(3)].map((_, i) => (
              <Skeleton key={i} variant="text" width="100%" height={24} />
            ))}
          </Box>
        ) : isError ? (
          <Alert severity="warning" variant="outlined">
            Unable to read battery data
          </Alert>
        ) : (
          <Box>
            {/* SoC progress bar */}
            {soc != null && (
              <Box sx={{ mb: 2 }}>
                <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                  <Typography variant="body2" color="text.secondary">
                    State of Charge
                  </Typography>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
                    {soc.toFixed(1)}%
                  </Typography>
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(soc, 100)}
                  color={getSocColor(soc)}
                  sx={{ height: 10, borderRadius: 5 }}
                />
              </Box>
            )}

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              {batteryV != null && (
                <MetricRow label="Battery Voltage" value={`${batteryV.toFixed(1)} V`} />
              )}
              {storageAvailable != null && (
                <MetricRow label="Available Storage" value={`${storageAvailable.toFixed(1)} Ah`} />
              )}
              {minReserve != null && (
                <MetricRow label="Min. Reserve" value={`${minReserve.toFixed(1)}%`} />
              )}
            </Box>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

function MetricRow({ label, value }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 500, fontFamily: 'monospace' }}>
        {value}
      </Typography>
    </Box>
  );
}

export default BatteryStatusCard;
