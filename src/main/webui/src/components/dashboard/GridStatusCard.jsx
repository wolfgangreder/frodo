import React from 'react';
import {
  Card,
  CardContent,
  Box,
  Typography,
  Skeleton,
  Alert,
  Stack,
} from '@mui/material';
import ElectricalServicesIcon from '@mui/icons-material/ElectricalServices';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';

/**
 * Formats a Wh value with appropriate scaling
 */
function formatEnergy(value) {
  if (value == null) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000000) return `${(num / 1000000).toFixed(2)} MWh`;
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(1)} kWh`;
  return `${num.toFixed(0)} Wh`;
}

/**
 * Determines grid status from connection bits and power values
 */
function getGridStatus(pvConn, ecpConn) {
  // PVConn and ECPConn are bitfields
  const connected = ecpConn != null && ecpConn > 0;
  return connected;
}

/**
 * GridStatusCard - shows grid connection status and energy totals from Model 122
 *
 * @param {Object} props
 * @param {Object} props.statusData - SunSpec Status model (122) response
 * @param {Object} props.inverterData - Inverter model data (for current grid power estimate)
 * @param {boolean} props.isLoading - Whether data is still loading
 * @param {boolean} props.isError - Whether a fetch error occurred
 */
function GridStatusCard({ statusData, inverterData, isLoading, isError }) {
  const statusFields = statusData?.fields || {};
  const inverterFields = inverterData?.fields || {};

  const pvConn = statusFields.PVConn;
  const ecpConn = statusFields.ECPConn;
  const storConn = statusFields.StorConn;
  const actWh = statusFields.ActWh;
  const actVAh = statusFields.ActVAh;

  // Current AC power from inverter as proxy for grid interaction
  const acPower = inverterFields.W != null ? parseFloat(inverterFields.W) : null;
  const isExporting = acPower != null && acPower > 0;
  const gridConnected = getGridStatus(pvConn, ecpConn);

  const gridStatusLabel = gridConnected
    ? isExporting
      ? 'Exporting'
      : 'Connected'
    : 'Disconnected';

  const gridStatusColor = gridConnected
    ? isExporting
      ? 'success.main'
      : 'info.main'
    : 'error.main';

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <ElectricalServicesIcon
              sx={{ color: gridConnected ? 'success.main' : 'text.disabled' }}
            />
            <Typography variant="h6" sx={{ color: 'primary.main' }}>
              Grid
            </Typography>
          </Stack>
          <Typography variant="caption" sx={{ color: gridStatusColor, fontWeight: 600 }}>
            {gridStatusLabel}
          </Typography>
        </Stack>

        {isLoading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {[...Array(4)].map((_, i) => (
              <Skeleton key={i} variant="text" width="100%" height={24} />
            ))}
          </Box>
        ) : isError ? (
          <Alert severity="warning" variant="outlined">
            Unable to read grid status data
          </Alert>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {/* Current AC power with direction indicator */}
            {acPower != null && (
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  AC Power
                </Typography>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  {isExporting ? (
                    <ArrowUpwardIcon sx={{ fontSize: 16, color: 'success.main' }} />
                  ) : (
                    <ArrowDownwardIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                  )}
                  <Typography
                    variant="subtitle1"
                    sx={{
                      fontWeight: 700,
                      fontFamily: 'monospace',
                      color: isExporting ? 'success.main' : 'warning.main',
                    }}
                  >
                    {Math.abs(acPower) >= 1000
                      ? `${(Math.abs(acPower) / 1000).toFixed(1)} kW`
                      : `${Math.abs(acPower).toFixed(0)} W`}
                  </Typography>
                </Stack>
              </Box>
            )}

            {/* Connection status bits */}
            <MetricRow
              label="PV Connected"
              value={pvConn != null && pvConn > 0 ? 'Yes' : pvConn != null ? 'No' : '-'}
            />
            <MetricRow
              label="Grid Connected"
              value={ecpConn != null && ecpConn > 0 ? 'Yes' : ecpConn != null ? 'No' : '-'}
            />
            {storConn != null && (
              <MetricRow
                label="Storage Connected"
                value={storConn > 0 ? 'Yes' : 'No'}
              />
            )}

            {/* Lifetime energy */}
            {actWh != null && (
              <Box sx={{ mt: 1 }}>
                <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
                  Lifetime Energy
                </Typography>
                <MetricRow label="Active Energy" value={formatEnergy(actWh)} />
                {actVAh != null && (
                  <MetricRow label="Apparent Energy" value={formatEnergy(actVAh)} />
                )}
              </Box>
            )}
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

export default GridStatusCard;
