import React, { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Box,
  Typography,
  Skeleton,
  Alert,
  Stack,
  Switch,
  Tooltip,
  Collapse,
  IconButton,
  TextField,
  Button,
  Divider,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import ElectricalServicesIcon from '@mui/icons-material/ElectricalServices';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import BlockIcon from '@mui/icons-material/Block';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import DeleteIcon from '@mui/icons-material/Delete';
import {
  useSunSpecControls,
  useSetPowerLimit,
  useExportSchedule,
  useSetExportSchedule,
  useDeleteExportSchedule,
  useCurrentMarketPrice,
} from '../../hooks/useSunSpec';

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
 * Determines grid status from connection bits
 */
function getGridStatus(_pvConn, ecpConn) {
  return ecpConn != null && ecpConn > 0;
}

/**
 * GridStatusCard - shows grid connection status, energy totals, and controls.
 *
 * When Model 123 (Immediate Controls) is available the card shows:
 *   • A toggle to manually block / allow grid export immediately.
 *   • A collapsible "Schedule" section to configure a daily recurring window
 *     during which export is automatically blocked.
 *
 * @param {Object}  props
 * @param {number}  props.deviceId      - Device ID (required for control mutations)
 * @param {Object}  props.statusData    - SunSpec Status model (122) response
 * @param {Object}  props.inverterData  - Inverter model data (for current power)
 * @param {boolean} props.isLoading     - Whether status data is still loading
 * @param {boolean} props.isError       - Whether a status fetch error occurred
 * @param {boolean} props.hasControls   - Whether the device has Model 123
 */
function GridStatusCard({ deviceId, statusData, inverterData, isLoading, isError, hasControls }) {
  const statusFields = statusData?.fields || {};
  const inverterFields = inverterData?.fields || {};

  const pvConn  = statusFields.PVConn;
  const ecpConn = statusFields.ECPConn;
  const storConn = statusFields.StorConn;
  const actWh   = statusFields.ActWh;
  const actVAh  = statusFields.ActVAh;

  const acPower    = inverterFields.W != null ? parseFloat(inverterFields.W) : null;
  const isExporting = acPower != null && acPower > 0;
  const gridConnected = getGridStatus(pvConn, ecpConn);

  const gridStatusLabel = gridConnected
    ? isExporting ? 'Exporting' : 'Connected'
    : 'Disconnected';
  const gridStatusColor = gridConnected
    ? isExporting ? 'success.main' : 'info.main'
    : 'error.main';

  // ── Model 123 state ──────────────────────────────────────────────────────
  const controlsQuery = useSunSpecControls(deviceId, hasControls);
  const controlsFields = controlsQuery.data?.fields || {};
  const exportBlocked  = hasControls && Number(controlsFields.WMaxLim_Ena) === 1;
  const controlsLoading = hasControls && controlsQuery.isLoading;

  const setPowerLimitMutation = useSetPowerLimit(deviceId);
  const isToggling = setPowerLimitMutation.isPending;

  const handleToggleExport = () => {
    if (exportBlocked) {
      setPowerLimitMutation.mutate({ enable: false });
    } else {
      // Use the current form strategy (which reflects the saved schedule once loaded,
      // or the default FIXED_LIMIT for new devices without a schedule).
      // For FIXED_LIMIT: pass the watt cap so the server converts it to % of WMax
      //   without needing a Smart Meter.
      // For ZERO_EXPORT_DYNAMIC: omit limitWatts so the server reads the Smart Meter.
      const opts = { enable: true };
      if (formStrategy === 'FIXED_LIMIT') {
        opts.limitWatts = formLimitWatts || 500;
      }
      setPowerLimitMutation.mutate(opts);
    }
  };

  // ── Schedule section ─────────────────────────────────────────────────────
  const [scheduleOpen, setScheduleOpen] = useState(false);

  const scheduleQuery      = useExportSchedule(deviceId, hasControls);
  const setScheduleMutation    = useSetExportSchedule(deviceId);
  const deleteScheduleMutation = useDeleteExportSchedule(deviceId);

  const existingSchedule = scheduleQuery.data; // null = no schedule, object = has schedule

  // Local form state, initialised from server data when available
  const [formEnabled,        setFormEnabled]        = useState(true);
  const [formBlockFrom,      setFormBlockFrom]      = useState('11:00');
  const [formEnableFrom,     setFormEnableFrom]     = useState('15:00');
  const [formStrategy,       setFormStrategy]       = useState('FIXED_LIMIT');
  const [formLimitWatts,     setFormLimitWatts]     = useState(500);
  const [formToleranceWatts, setFormToleranceWatts] = useState(50);

  // Keep form in sync when server data arrives or the panel is opened
  useEffect(() => {
    if (existingSchedule) {
      setFormEnabled(existingSchedule.enabled);
      setFormBlockFrom(existingSchedule.blockFrom);
      setFormEnableFrom(existingSchedule.enableFrom);
      setFormStrategy(existingSchedule.strategy || 'ZERO_EXPORT_DYNAMIC');
      setFormLimitWatts(existingSchedule.limitWatts || 500);
      setFormToleranceWatts(existingSchedule.exportToleranceWatts ?? 50);
    }
  }, [existingSchedule]);

  const handleSaveSchedule = () => {
    const payload = {
      enabled:  formEnabled,
      strategy: formStrategy,
    };
    if (formStrategy !== 'PRICE_CONTROLLED') {
      payload.blockFrom  = formBlockFrom;
      payload.enableFrom = formEnableFrom;
    }
    if (formStrategy === 'FIXED_LIMIT') {
      payload.limitWatts = Math.max(1, parseInt(formLimitWatts, 10) || 500);
    }
    if (formStrategy === 'PRICE_CONTROLLED') {
      payload.exportToleranceWatts = Math.max(0, parseInt(formToleranceWatts, 10) || 50);
    }
    setScheduleMutation.mutate(payload);
  };

  const handleDeleteSchedule = () => {
    deleteScheduleMutation.mutate();
  };

  const isSaving   = setScheduleMutation.isPending;
  const isDeleting = deleteScheduleMutation.isPending;

  // Current market price — only fetched when a PRICE_CONTROLLED schedule is active
  const isPriceControlled = existingSchedule?.strategy === 'PRICE_CONTROLLED'
    || formStrategy === 'PRICE_CONTROLLED';
  const marketPriceQuery = useCurrentMarketPrice(isPriceControlled && scheduleOpen);
  const currentPrice = marketPriceQuery.data ?? null;

  // Active schedule indicator shown next to the section toggle
  const scheduleActive = existingSchedule?.enabled;
  const scheduledBlocked = existingSchedule?.currentlyBlocked;

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        {/* Header */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <ElectricalServicesIcon
              sx={{ color: gridConnected ? 'success.main' : 'text.disabled' }}
            />
            <Typography variant="h6" sx={{ color: 'primary.main' }}>
              Grid
            </Typography>
          </Stack>

          <Stack direction="row" spacing={0.5} alignItems="center">
            {exportBlocked && (
              <Tooltip title="Grid export is currently blocked">
                <BlockIcon sx={{ fontSize: 16, color: 'warning.main' }} />
              </Tooltip>
            )}
            <Typography variant="caption" sx={{ color: gridStatusColor, fontWeight: 600 }}>
              {gridStatusLabel}
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
          <Alert severity="warning" variant="outlined">
            Unable to read grid status data
          </Alert>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {/* Current AC power */}
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
                    sx={{ fontWeight: 700, fontFamily: 'monospace',
                      color: isExporting ? 'success.main' : 'warning.main' }}
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
              <MetricRow label="Storage Connected" value={storConn > 0 ? 'Yes' : 'No'} />
            )}

            {/* Lifetime energy */}
            {actWh != null && (
              <Box sx={{ mt: 1 }}>
                <Typography variant="caption" color="text.secondary"
                  sx={{ mb: 0.5, display: 'block' }}>
                  Lifetime Energy
                </Typography>
                <MetricRow label="Active Energy" value={formatEnergy(actWh)} />
                {actVAh != null && (
                  <MetricRow label="Apparent Energy" value={formatEnergy(actVAh)} />
                )}
              </Box>
            )}

            {/* ── Controls section (Model 123) ─────────────────────────── */}
            {hasControls && (
              <Box sx={{ mt: 1.5 }}>
                <Divider sx={{ mb: 1.5 }} />

                {/* Manual toggle */}
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

                {/* Schedule toggle row */}
                <Box
                  sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    mt: 0.5,
                  }}
                >
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <ScheduleIcon
                      sx={{
                        fontSize: 16,
                        color: scheduleActive ? 'primary.main' : 'text.disabled',
                      }}
                    />
                    <Typography variant="body2" color="text.secondary">
                      Schedule
                    </Typography>
                    {scheduledBlocked && (
                      <Tooltip title="Schedule is currently blocking export">
                        <Typography variant="caption" sx={{ color: 'warning.main', fontWeight: 600 }}>
                          active
                        </Typography>
                      </Tooltip>
                    )}
                    {scheduleActive && !scheduledBlocked && (
                      <Typography variant="caption" color="text.disabled">
                        {existingSchedule.strategy === 'PRICE_CONTROLLED'
                          ? 'price-controlled'
                          : `${existingSchedule.blockFrom}–${existingSchedule.enableFrom}`}
                      </Typography>
                    )}
                  </Stack>
                  <IconButton
                    size="small"
                    onClick={() => setScheduleOpen((o) => !o)}
                    aria-label={scheduleOpen ? 'Collapse schedule' : 'Expand schedule'}
                  >
                    {scheduleOpen ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                  </IconButton>
                </Box>

                {/* Collapsible schedule form */}
                <Collapse in={scheduleOpen}>
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
                    {scheduleQuery.isLoading ? (
                      <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                        <CircularProgress size={20} />
                      </Box>
                    ) : (
                      <>
                        {/* Enable schedule toggle */}
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Typography variant="body2">Enable schedule</Typography>
                          <Switch
                            size="small"
                            checked={formEnabled}
                            onChange={(e) => setFormEnabled(e.target.checked)}
                            color="primary"
                          />
                        </Box>

                        {/* Time fields — hidden for PRICE_CONTROLLED */}
                        {formStrategy !== 'PRICE_CONTROLLED' && (
                          <Box sx={{ display: 'flex', gap: 1 }}>
                            <TextField
                              type="time"
                              label="Block from"
                              value={formBlockFrom}
                              onChange={(e) => setFormBlockFrom(e.target.value)}
                              size="small"
                              fullWidth
                              disabled={!formEnabled}
                              slotProps={{ inputLabel: { shrink: true } }}
                            />
                            <TextField
                              type="time"
                              label="Enable from"
                              value={formEnableFrom}
                              onChange={(e) => setFormEnableFrom(e.target.value)}
                              size="small"
                              fullWidth
                              disabled={!formEnabled}
                              slotProps={{ inputLabel: { shrink: true } }}
                            />
                          </Box>
                        )}

                        {/* Strategy selector */}
                        <FormControl size="small" fullWidth disabled={!formEnabled}>
                          <InputLabel id="strategy-label">Strategy</InputLabel>
                          <Select
                            labelId="strategy-label"
                            value={formStrategy}
                            label="Strategy"
                            onChange={(e) => setFormStrategy(e.target.value)}
                          >
                            <MenuItem value="ZERO_EXPORT_DYNAMIC">
                              Zero-export dynamic (Solar API)
                            </MenuItem>
                            <MenuItem value="FIXED_LIMIT">
                              Hard block (fixed watt cap)
                            </MenuItem>
                          </Select>
                        </FormControl>

                        {/* Fixed watt cap input — only shown for FIXED_LIMIT */}
                        {formStrategy === 'FIXED_LIMIT' && (
                          <TextField
                            label="Power cap (W)"
                            type="number"
                            size="small"
                            value={formLimitWatts}
                            onChange={(e) => setFormLimitWatts(e.target.value)}
                            inputProps={{ min: 1, step: 100 }}
                            helperText="Max inverter output during the block window (default: 500 W)"
                            disabled={!formEnabled}
                          />
                        )}

                        {/* Export tolerance input — only shown for PRICE_CONTROLLED */}
                        {formStrategy === 'PRICE_CONTROLLED' && (
                          <TextField
                            label="Export tolerance (W)"
                            type="number"
                            size="small"
                            value={formToleranceWatts}
                            onChange={(e) => setFormToleranceWatts(e.target.value)}
                            inputProps={{ min: 0, step: 10 }}
                            helperText="Allowed grid export above load demand when price is negative (default: 50 W)"
                            disabled={!formEnabled}
                          />
                        )}

                        {/* Current market price — only shown for PRICE_CONTROLLED */}
                        {formStrategy === 'PRICE_CONTROLLED' && (
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
                        )}

                        <Typography variant="caption" color="text.secondary">
                          {formStrategy === 'PRICE_CONTROLLED'
                            ? <>Price-controlled: allow up to{' '}
                                <strong>{Math.max(0, parseInt(formToleranceWatts, 10) || 50)} W</strong>{' '}
                                above load demand when aWATTar AT price is negative</>
                            : formStrategy === 'FIXED_LIMIT'
                              ? <>Cap to{' '}
                                  <strong>{Math.max(1, parseInt(formLimitWatts, 10) || 500)} W</strong> from{' '}
                                  <strong>{formBlockFrom}</strong> until{' '}
                                  <strong>{formEnableFrom}</strong>
                                  {formBlockFrom > formEnableFrom && ' (crosses midnight)'}
                                </>
                              : <>Zero-export (dynamic) from{' '}
                                  <strong>{formBlockFrom}</strong> until{' '}
                                  <strong>{formEnableFrom}</strong>
                                  {formBlockFrom > formEnableFrom && ' (crosses midnight)'}
                                </>
                          }
                        </Typography>

                        {/* Action buttons */}
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          {existingSchedule && (
                            <Tooltip title="Delete schedule">
                              <span>
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={handleDeleteSchedule}
                                  disabled={isDeleting || isSaving}
                                  aria-label="Delete schedule"
                                >
                                  {isDeleting
                                    ? <CircularProgress size={16} />
                                    : <DeleteIcon fontSize="small" />}
                                </IconButton>
                              </span>
                            </Tooltip>
                          )}
                          <Button
                            size="small"
                            variant="contained"
                            onClick={handleSaveSchedule}
                            disabled={isSaving || isDeleting}
                            startIcon={isSaving ? <CircularProgress size={14} /> : null}
                          >
                            Save
                          </Button>
                        </Stack>
                      </>
                    )}
                  </Box>
                </Collapse>
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
