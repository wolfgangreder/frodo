import React, { useMemo } from 'react';
import {
  Card,
  CardContent,
  Box,
  Typography,
  Skeleton,
  Alert,
  Stack,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import BoltIcon from '@mui/icons-material/Bolt';

/**
 * Formats a numeric value with appropriate decimal places and unit
 */
function formatValue(value, unit, decimals = 1) {
  if (value == null || value === '' || (typeof value === 'number' && isNaN(value))) {
    return '-';
  }
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';

  // Auto-scale large watt-hour values
  if (unit === 'Wh' && Math.abs(num) >= 1000000) {
    return `${(num / 1000000).toFixed(1)} MWh`;
  }
  if (unit === 'Wh' && Math.abs(num) >= 1000) {
    return `${(num / 1000).toFixed(1)} kWh`;
  }
  // Auto-scale large watt values
  if (unit === 'W' && Math.abs(num) >= 1000) {
    return `${(num / 1000).toFixed(decimals)} kW`;
  }

  return `${num.toFixed(decimals)} ${unit}`;
}

/**
 * Maps inverter operating state enum to display label and color
 */
function getOperatingState(st) {
  if (st == null) return { label: 'Unknown', color: 'text.disabled' };
  const stateMap = {
    1: { label: 'Off', color: 'text.disabled' },
    2: { label: 'Sleeping', color: 'warning.main' },
    3: { label: 'Starting', color: 'warning.main' },
    4: { label: 'Running (MPPT)', color: 'success.main' },
    5: { label: 'Throttled', color: 'warning.main' },
    6: { label: 'Shutting Down', color: 'warning.main' },
    7: { label: 'Fault', color: 'error.main' },
    8: { label: 'Standby', color: 'info.main' },
  };
  return stateMap[st] || { label: `State ${st}`, color: 'text.secondary' };
}

/**
 * PowerMetricsCard - displays real-time AC/DC power, voltage, current, frequency, energy
 *
 * @param {Object} props
 * @param {Object} props.inverterData - SunSpec inverter model response (fields map)
 * @param {boolean} props.isLoading - Whether data is still loading
 * @param {boolean} props.isError - Whether a fetch error occurred
 */
function PowerMetricsCard({ inverterData, isLoading, isError }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const fields = inverterData?.fields || {};

  const state = useMemo(() => getOperatingState(fields.St), [fields.St]);

  const isGenerating = fields.St === 4 && fields.W != null && fields.W > 0;

  // Primary metrics always shown
  const primaryMetrics = [
    { label: 'AC Power', value: fields.W, unit: 'W', decimals: 0 },
    { label: 'Energy Total', value: fields.WH, unit: 'Wh', decimals: 0 },
  ];

  // Secondary metrics hidden on mobile
  const secondaryMetrics = [
    { label: 'AC Voltage', value: fields.PhVphA, unit: 'V', decimals: 1 },
    { label: 'AC Current', value: fields.A, unit: 'A', decimals: 2 },
    { label: 'Frequency', value: fields.Hz, unit: 'Hz', decimals: 2 },
    { label: 'Power Factor', value: fields.PF, unit: '%', decimals: 1 },
  ];

  const dcMetrics = [
    { label: 'DC Voltage', value: fields.DCV, unit: 'V', decimals: 1 },
    { label: 'DC Current', value: fields.DCA, unit: 'A', decimals: 2 },
    { label: 'DC Power', value: fields.DCW, unit: 'W', decimals: 0 },
  ];

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <BoltIcon sx={{ color: isGenerating ? 'success.main' : 'text.disabled' }} />
            <Typography variant="h6" sx={{ color: 'primary.main' }}>
              Power
            </Typography>
          </Stack>
          <Typography variant="caption" sx={{ color: state.color, fontWeight: 600 }}>
            {state.label}
          </Typography>
        </Stack>

        {isLoading ? (
          <MetricsSkeleton count={isMobile ? 2 : 6} />
        ) : isError ? (
          <Alert severity="warning" variant="outlined">
            Unable to read inverter data
          </Alert>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {/* Primary metrics - always visible */}
            {primaryMetrics.map((m) => (
              <MetricRow key={m.label} label={m.label} value={formatValue(m.value, m.unit, m.decimals)} primary />
            ))}

            {/* Secondary + DC metrics - hidden on mobile */}
            {!isMobile && (
              <>
                {secondaryMetrics.map((m) => (
                  <MetricRow key={m.label} label={m.label} value={formatValue(m.value, m.unit, m.decimals)} />
                ))}
                {dcMetrics.some((m) => m.value != null) && (
                  <>
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 1, mb: 0.5 }}>
                      DC Side
                    </Typography>
                    {dcMetrics.map((m) => (
                      <MetricRow key={m.label} label={m.label} value={formatValue(m.value, m.unit, m.decimals)} />
                    ))}
                  </>
                )}
              </>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

function MetricRow({ label, value, primary = false }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant={primary ? 'subtitle1' : 'body2'}
        sx={{ fontWeight: primary ? 700 : 500, fontFamily: 'monospace' }}
      >
        {value}
      </Typography>
    </Box>
  );
}

function MetricsSkeleton({ count }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {[...Array(count)].map((_, i) => (
        <Skeleton key={i} variant="text" width="100%" height={24} />
      ))}
    </Box>
  );
}

export default PowerMetricsCard;
