import React, { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Box,
  Typography,
  Skeleton,
  Stack,
  Tooltip,
  Switch,
  Alert,
  Collapse,
  CircularProgress,
  TextField,
  IconButton,
  Divider,
} from '@mui/material';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import BlockIcon from '@mui/icons-material/Block';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import PriceChangeIcon from '@mui/icons-material/PriceChange';
import SaveIcon from '@mui/icons-material/Save';
import { useSolarApiStatus } from '../../hooks/useSolarApi';
import {
  useSunSpecControls,
  useSetPowerLimit,
  useCurrentMarketPrice,
} from '../../hooks/useSunSpec';
import { usePriceControl, useSetPriceControl } from '../../hooks/usePriceControl';

/**
 * Formats power in appropriate units
 */
function formatPower(value) {
  if (value == null) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(2)} kW`;
  return `${num.toFixed(0)} W`;
}

/**
 * Formats percentage
 */
function formatPercent(value) {
  if (value == null) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  return `${num.toFixed(0)}%`;
}

/**
 * SitePowerFlowCard - displays Solar API site power flow with block export controls.
 *
 * Shows site-level power flow data from Solar API and provides quick access
 * to block export toggle via SunSpec Controls, plus a global price-control
 * configuration section for aWATTar AT price-based export limiting.
 *
 * @param {Object} props
 * @param {number} props.deviceId - Device ID
 * @param {Object} props.statusData - SunSpec Status model (122) for export block state
 * @param {boolean} props.hasControls - Whether Model 123 is available
 */
function SitePowerFlowCard({ deviceId, statusData, hasControls }) {
  const { data: solarStatus, isLoading, isError } = useSolarApiStatus();
  const site = solarStatus?.site;

  // WMaxLim_Ena lives in Model 123 (Controls), not in statusData (Model 122 / Status)
  const controlsQuery = useSunSpecControls(deviceId, hasControls);
  const controlsLoading = hasControls && controlsQuery.isLoading;
  const controlsFields = controlsQuery.data?.fields || {};

  const exportBlocked = hasControls && Number(controlsFields.WMaxLim_Ena) === 1;

  const setPowerLimitMutation = useSetPowerLimit(deviceId);
  const isToggling = setPowerLimitMutation.isPending;

  const handleToggleExport = () => {
    if (exportBlocked) {
      setPowerLimitMutation.mutate({ enable: false });
    } else {
      setPowerLimitMutation.mutate({ enable: true });
    }
  };

  // ── Price Control ──────────────────────────────────────────────────────────
  const [pcOpen, setPcOpen] = useState(false);
  const [formPcEnabled, setFormPcEnabled] = useState(false);
  const [formPcTolerance, setFormPcTolerance] = useState(50);

  const priceControlQuery = usePriceControl();
  const setPriceControlMutation = useSetPriceControl();
  const isSavingPc = setPriceControlMutation.isPending;

  // Sync form from server data
  useEffect(() => {
    if (priceControlQuery.data) {
      setFormPcEnabled(priceControlQuery.data.enabled);
      setFormPcTolerance(priceControlQuery.data.exportToleranceWatts ?? 50);
    }
  }, [priceControlQuery.data]);

  const marketPriceQuery = useCurrentMarketPrice(pcOpen);
  const currentPrice = marketPriceQuery.data ?? null;

  const handleSavePc = () => {
    setPriceControlMutation.mutate({
      enabled: formPcEnabled,
      exportToleranceWatts: Math.max(0, parseInt(String(formPcTolerance), 10) || 50),
    });
  };

  // Summary of price control state for the collapsed header
  const pcData = priceControlQuery.data;
  const pcCurrentlyBlocking = pcData?.currentlyBlocking ?? false;
  const pcEnabled = pcData?.enabled ?? false;

  // ── Power values ───────────────────────────────────────────────────────────
  const gridW = site?.gridPowerWatts;
  const loadW = site?.loadPowerWatts;
  const pvW = site?.pvPowerWatts;
  const battW = site?.batteryPowerWatts;
  const autonomy = site?.autonomyPercent;
  const selfConsumption = site?.selfConsumptionPercent;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        {/* Header */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <ShowChartIcon sx={{ color: 'primary.main' }} />
            <Typography variant="h6" sx={{ color: 'primary.main' }}>
              Site Power Flow
            </Typography>
          </Stack>

          <Stack direction="row" spacing={0.5} alignItems="center">
            {exportBlocked && (
              <Tooltip title="Grid export is currently blocked">
                <BlockIcon sx={{ fontSize: 16, color: 'warning.main' }} />
              </Tooltip>
            )}
            <Typography variant="caption" sx={{ color: exportBlocked ? 'warning.main' : 'text.secondary', fontWeight: 600 }}>
              {exportBlocked ? 'Blocked' : 'Active'}
            </Typography>
          </Stack>
        </Stack>

        {/* Body */}
        {isLoading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {[...Array(4)].map((_, i) => (
              <Skeleton key={i} variant="text" width="100%" height={24} />
            ))}
          </Box>
        ) : isError ? (
          <Typography variant="body2" color="text.secondary">
            Solar API unavailable
          </Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {/* Power flow values */}
            <MetricRow label="Grid" value={formatPower(gridW)} color={gridW < 0 ? 'success.main' : gridW > 0 ? 'warning.main' : null} />
            <MetricRow label="Load" value={formatPower(loadW)} color={loadW < 0 ? 'error.main' : null} />
            <MetricRow label="PV" value={formatPower(pvW)} color="success.main" />
            <MetricRow label="Battery" value={formatPower(battW)} color={battW > 0 ? 'warning.main' : battW < 0 ? 'success.main' : null} />

            {/* Site Statistics */}
            {(autonomy != null || selfConsumption != null) && (
              <Box sx={{ mt: 1 }}>
                <Divider sx={{ mb: 1.5 }} />
                <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
                  Site Statistics
                </Typography>
                <MetricRow label="Autonomy" value={formatPercent(autonomy)} />
                <MetricRow label="Self-consumption" value={formatPercent(selfConsumption)} />
              </Box>
            )}

            {/* Block Export Control */}
            {hasControls && (
              <Box sx={{ mt: 1 }}>
                <Divider sx={{ mb: 1.5 }} />
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    Block export
                  </Typography>
                  <Tooltip
                    title={
                      controlsLoading ? 'Loading…'
                        : exportBlocked
                          ? 'Export blocked — click to re-enable'
                          : 'Export active — click to block'
                    }
                  >
                    <span>
                      <Switch
                        size="small"
                        checked={exportBlocked}
                        onChange={handleToggleExport}
                        disabled={controlsLoading || isToggling}
                        color="warning"
                        inputProps={{ 'aria-label': 'Block grid export' }}
                      />
                    </span>
                  </Tooltip>
                </Box>
              </Box>
            )}

            {/* Price Control Section */}
            <Box sx={{ mt: 1 }}>
              <Divider sx={{ mb: 1.5 }} />
              {/* Collapsible header */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <PriceChangeIcon
                    sx={{ fontSize: 16, color: pcEnabled ? 'primary.main' : 'text.disabled' }}
                  />
                  <Typography variant="body2" color="text.secondary">
                    Price control
                  </Typography>
                  {pcCurrentlyBlocking && (
                    <Tooltip title="Price control is currently limiting export">
                      <Typography variant="caption" sx={{ color: 'warning.main', fontWeight: 600 }}>
                        blocking
                      </Typography>
                    </Tooltip>
                  )}
                  {pcEnabled && !pcCurrentlyBlocking && (
                    <Typography variant="caption" color="text.disabled">
                      enabled
                    </Typography>
                  )}
                </Stack>
                <IconButton
                  size="small"
                  onClick={() => setPcOpen((o) => !o)}
                  aria-label={pcOpen ? 'Collapse price control' : 'Expand price control'}
                >
                  {pcOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                </IconButton>
              </Box>

              {/* Collapsible form */}
              <Collapse in={pcOpen}>
                <Box
                  sx={{
                    mt: 1,
                    p: 1.5,
                    bgcolor: 'action.hover',
                    borderRadius: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1.5,
                  }}
                >
                  {priceControlQuery.isLoading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                      <CircularProgress size={20} />
                    </Box>
                  ) : (
                    <>
                      {/* Enable toggle */}
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Typography variant="body2">Enable price control</Typography>
                        <Switch
                          size="small"
                          checked={formPcEnabled}
                          onChange={(e) => setFormPcEnabled(e.target.checked)}
                          color="primary"
                        />
                      </Box>

                      {/* Tolerance input */}
                      <TextField
                        label="Export tolerance (W)"
                        type="number"
                        size="small"
                        value={formPcTolerance}
                        onChange={(e) => setFormPcTolerance(e.target.value)}
                        inputProps={{ min: 0, step: 10 }}
                        helperText="Allowed export above load+battery demand when price is negative (default: 50 W)"
                        disabled={!formPcEnabled}
                      />

                      {/* Current market price */}
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Typography variant="body2" color="text.secondary">
                          Current price
                        </Typography>
                        {marketPriceQuery.isLoading ? (
                          <CircularProgress size={14} />
                        ) : currentPrice == null ? (
                          <Typography variant="caption" color="text.disabled">
                            not available
                          </Typography>
                        ) : (
                          <Tooltip title={`Valid ${currentPrice.startTime} – ${currentPrice.endTime}`}>
                            <Typography
                              variant="body2"
                              sx={{
                                fontFamily: 'monospace',
                                fontWeight: 700,
                                color: currentPrice.priceCt < 0 ? 'warning.main' : 'success.main',
                              }}
                            >
                              {currentPrice.priceCt.toFixed(2)} ct/kWh
                              {currentPrice.priceCt < 0 ? ' (blocking)' : ' (normal)'}
                            </Typography>
                          </Tooltip>
                        )}
                      </Box>

                      {/* Summary */}
                      <Typography variant="caption" color="text.secondary">
                        {formPcEnabled
                          ? <>When aWATTar AT price is negative, export is capped to{' '}
                              <strong>{Math.max(0, parseInt(String(formPcTolerance), 10) || 50)} W</strong> above load demand.</>
                          : 'Price-controlled export limiting is disabled.'}
                      </Typography>

                      {/* Save button */}
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                        <Tooltip title="Save price control setting">
                          <span>
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={handleSavePc}
                              disabled={isSavingPc}
                              aria-label="Save price control"
                            >
                              {isSavingPc ? <CircularProgress size={16} /> : <SaveIcon fontSize="small" />}
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Box>
                    </>
                  )}
                </Box>
              </Collapse>
            </Box>

          </Box>
        )}
      </CardContent>
    </Card>
  );
}

function MetricRow({ label, value, color }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 500, fontFamily: 'monospace', color }}>
        {value}
      </Typography>
    </Box>
  );
}

export default SitePowerFlowCard;
