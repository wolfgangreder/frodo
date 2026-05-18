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
  Card,
  CardBody,
  Skeleton,
  Alert,
  Switch,
  Tooltip,
  Button,
  Divider,
  Spinner,
  TextInput,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Select,
  SelectList,
  SelectOption,
  MenuToggle,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import {
  PlugIcon,
  ArrowUpIcon,
  ArrowDownIcon,
  BanIcon,
  ClockIcon,
  AngleDownIcon,
  AngleUpIcon,
  TrashIcon,
} from '@patternfly/react-icons';
import {
  useSunSpecControls,
  useSetPowerLimit,
  useExportSchedule,
  useSetExportSchedule,
  useDeleteExportSchedule,
  useCurrentMarketPrice,
} from '../../hooks/useSunSpec';

// PF v6 design token CSS variable references
const C = {
  primary:  'var(--pf-t--global--color--brand--default, #0066cc)',
  success:  'var(--pf-t--global--color--status--success--default, #3e8635)',
  warning:  'var(--pf-t--global--color--status--warning--default, #f0ab00)',
  danger:   'var(--pf-t--global--color--status--danger--default, #c9190b)',
  info:     'var(--pf-t--global--color--status--info--default, #0066cc)',
  subtle:   'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text-color--disabled, #b8bbbe)',
};

const STRATEGY_LABELS = {
  ZERO_EXPORT_DYNAMIC: 'Zero-export dynamic (Solar API)',
  FIXED_LIMIT:         'Hard block (fixed watt cap)',
  PRICE_CONTROLLED:    'Price-controlled (aWATTar AT)',
};

function formatEnergy(value) {
  if (value == null) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000000) return `${(num / 1000000).toFixed(2)} MWh`;
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(1)} kWh`;
  return `${num.toFixed(0)} Wh`;
}

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

  const pvConn   = statusFields.PVConn;
  const ecpConn  = statusFields.ECPConn;
  const storConn = statusFields.StorConn;
  const actWh    = statusFields.ActWh;
  const actVAh   = statusFields.ActVAh;

  const acPower     = inverterFields.W != null ? parseFloat(inverterFields.W) : null;
  const isExporting = acPower != null && acPower > 0;
  const gridConnected = getGridStatus(pvConn, ecpConn);

  const gridStatusLabel = gridConnected
    ? isExporting ? 'Exporting' : 'Connected'
    : 'Disconnected';
  const gridStatusColor = gridConnected
    ? isExporting ? C.success : C.info
    : C.danger;

  // ── Model 123 state ──────────────────────────────────────────────────────
  const controlsQuery   = useSunSpecControls(deviceId, hasControls);
  const controlsFields  = controlsQuery.data?.fields || {};
  const exportBlocked   = hasControls && Number(controlsFields.WMaxLim_Ena) === 1;
  const controlsLoading = hasControls && controlsQuery.isLoading;

  const setPowerLimitMutation = useSetPowerLimit(deviceId);
  const isToggling = setPowerLimitMutation.isPending;

  const handleToggleExport = () => {
    if (exportBlocked) {
      setPowerLimitMutation.mutate({ enable: false });
    } else {
      const opts = { enable: true };
      if (formStrategy === 'FIXED_LIMIT') {
        opts.limitWatts = formLimitWatts || 500;
      }
      setPowerLimitMutation.mutate(opts);
    }
  };

  // ── Schedule section ─────────────────────────────────────────────────────
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [strategyOpen, setStrategyOpen] = useState(false);

  const scheduleQuery          = useExportSchedule(deviceId, hasControls);
  const setScheduleMutation    = useSetExportSchedule(deviceId);
  const deleteScheduleMutation = useDeleteExportSchedule(deviceId);

  const existingSchedule = scheduleQuery.data;

  const [formEnabled,        setFormEnabled]        = useState(true);
  const [formBlockFrom,      setFormBlockFrom]      = useState('11:00');
  const [formEnableFrom,     setFormEnableFrom]     = useState('15:00');
  const [formStrategy,       setFormStrategy]       = useState('FIXED_LIMIT');
  const [formLimitWatts,     setFormLimitWatts]     = useState(500);
  const [formToleranceWatts, setFormToleranceWatts] = useState(50);

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

  const isPriceControlled = existingSchedule?.strategy === 'PRICE_CONTROLLED'
    || formStrategy === 'PRICE_CONTROLLED';
  const marketPriceQuery = useCurrentMarketPrice(isPriceControlled && scheduleOpen);
  const currentPrice = marketPriceQuery.data ?? null;

  const scheduleActive  = existingSchedule?.enabled;
  const scheduledBlocked = existingSchedule?.currentlyBlocked;

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        {/* Header */}
        <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }} style={{ marginBottom: '1rem' }}>
          <FlexItem>
            <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>
                <PlugIcon style={{ color: gridConnected ? C.success : C.disabled }} />
              </FlexItem>
              <FlexItem>
                <span style={{ fontWeight: 600, color: C.primary }}>Grid</span>
              </FlexItem>
            </Flex>
          </FlexItem>
          <FlexItem>
            <Flex gap={{ default: 'gapXs' }} alignItems={{ default: 'alignItemsCenter' }}>
              {exportBlocked && (
                <FlexItem>
                  <Tooltip content="Grid export is currently blocked">
                    <BanIcon style={{ fontSize: 16, color: C.warning }} />
                  </Tooltip>
                </FlexItem>
              )}
              <FlexItem>
                <span style={{ fontSize: '0.75rem', fontWeight: 600, color: gridStatusColor }}>
                  {gridStatusLabel}
                </span>
              </FlexItem>
            </Flex>
          </FlexItem>
        </Flex>

        {/* Body */}
        {isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {[...Array(4)].map((_, i) => <Skeleton key={i} height="1.5rem" />)}
          </div>
        ) : isError ? (
          <Alert variant="warning" isInline title="Unable to read grid status data" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            {/* Current AC power */}
            {acPower != null && (
              <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
                <FlexItem>
                  <span style={{ fontSize: '0.875rem', color: C.subtle }}>AC Power</span>
                </FlexItem>
                <FlexItem>
                  <Flex gap={{ default: 'gapXs' }} alignItems={{ default: 'alignItemsCenter' }}>
                    <FlexItem>
                      {isExporting
                        ? <ArrowUpIcon style={{ fontSize: 16, color: C.success }} />
                        : <ArrowDownIcon style={{ fontSize: 16, color: C.warning }} />
                      }
                    </FlexItem>
                    <FlexItem>
                      <span style={{ fontWeight: 700, fontFamily: 'monospace', color: isExporting ? C.success : C.warning }}>
                        {Math.abs(acPower) >= 1000
                          ? `${(Math.abs(acPower) / 1000).toFixed(1)} kW`
                          : `${Math.abs(acPower).toFixed(0)} W`}
                      </span>
                    </FlexItem>
                  </Flex>
                </FlexItem>
              </Flex>
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
              <div style={{ marginTop: '0.5rem' }}>
                <span style={{ fontSize: '0.75rem', color: C.subtle, display: 'block', marginBottom: '0.25rem' }}>
                  Lifetime Energy
                </span>
                <MetricRow label="Active Energy" value={formatEnergy(actWh)} />
                {actVAh != null && (
                  <MetricRow label="Apparent Energy" value={formatEnergy(actVAh)} />
                )}
              </div>
            )}

            {/* ── Controls section (Model 123) ─────────────────────────── */}
            {hasControls && (
              <div style={{ marginTop: '0.75rem' }}>
                <Divider style={{ marginBottom: '0.75rem' }} />

                {/* Manual toggle */}
                <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
                  <FlexItem>
                    <span style={{ fontSize: '0.875rem', color: C.subtle }}>Block export</span>
                  </FlexItem>
                  <FlexItem>
                    <Tooltip
                      content={
                        controlsLoading ? 'Loading…'
                          : exportBlocked
                            ? 'Export blocked — click to re-enable'
                            : 'Export active — click to block'
                      }
                    >
                      <span>
                        <Switch
                          id="block-export-switch"
                          isChecked={exportBlocked}
                          onChange={(_event, checked) => handleToggleExport()}
                          isDisabled={controlsLoading || isToggling}
                          aria-label="Block grid export"
                          hasCheckIcon
                        />
                      </span>
                    </Tooltip>
                  </FlexItem>
                </Flex>

                {/* Schedule toggle row */}
                <Flex
                  justifyContent={{ default: 'justifyContentSpaceBetween' }}
                  alignItems={{ default: 'alignItemsCenter' }}
                  style={{ marginTop: '0.25rem' }}
                >
                  <FlexItem>
                    <Flex gap={{ default: 'gapXs' }} alignItems={{ default: 'alignItemsCenter' }}>
                      <FlexItem>
                        <ClockIcon style={{ fontSize: 16, color: scheduleActive ? C.primary : C.disabled }} />
                      </FlexItem>
                      <FlexItem>
                        <span style={{ fontSize: '0.875rem', color: C.subtle }}>Schedule</span>
                      </FlexItem>
                      {scheduledBlocked && (
                        <FlexItem>
                          <Tooltip content="Schedule is currently blocking export">
                            <span style={{ fontSize: '0.75rem', color: C.warning, fontWeight: 600 }}>active</span>
                          </Tooltip>
                        </FlexItem>
                      )}
                      {scheduleActive && !scheduledBlocked && (
                        <FlexItem>
                          <span style={{ fontSize: '0.75rem', color: C.disabled }}>
                            {existingSchedule.strategy === 'PRICE_CONTROLLED'
                              ? 'price-controlled'
                              : `${existingSchedule.blockFrom}–${existingSchedule.enableFrom}`}
                          </span>
                        </FlexItem>
                      )}
                    </Flex>
                  </FlexItem>
                  <FlexItem>
                    <Button
                      variant="plain"
                      size="sm"
                      onClick={() => setScheduleOpen((o) => !o)}
                      aria-label={scheduleOpen ? 'Collapse schedule' : 'Expand schedule'}
                    >
                      {scheduleOpen ? <AngleUpIcon /> : <AngleDownIcon />}
                    </Button>
                  </FlexItem>
                </Flex>

                {/* Collapsible schedule form */}
                {scheduleOpen && (
                  <div
                    style={{
                      marginTop: '0.5rem',
                      padding: '0.75rem',
                      background: 'var(--pf-t--global--background--color--secondary--default, #f0f0f0)',
                      borderRadius: '4px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.75rem',
                    }}
                  >
                    {scheduleQuery.isLoading ? (
                      <div style={{ display: 'flex', justifyContent: 'center', padding: '0.5rem' }}>
                        <Spinner size="md" />
                      </div>
                    ) : (
                      <>
                        {/* Enable schedule toggle */}
                        <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
                          <FlexItem>
                            <span style={{ fontSize: '0.875rem' }}>Enable schedule</span>
                          </FlexItem>
                          <FlexItem>
                            <Switch
                              id="schedule-enabled-switch"
                              isChecked={formEnabled}
                              onChange={(_event, checked) => setFormEnabled(checked)}
                              hasCheckIcon
                            />
                          </FlexItem>
                        </Flex>

                        {/* Time fields — hidden for PRICE_CONTROLLED */}
                        {formStrategy !== 'PRICE_CONTROLLED' && (
                          <Flex gap={{ default: 'gapSm' }}>
                            <FlexItem grow={{ default: 'grow' }}>
                              <FormGroup label="Block from" fieldId="block-from">
                                <TextInput
                                  id="block-from"
                                  type="time"
                                  value={formBlockFrom}
                                  onChange={(_event, value) => setFormBlockFrom(value)}
                                  isDisabled={!formEnabled}
                                  aria-label="Block from time"
                                />
                              </FormGroup>
                            </FlexItem>
                            <FlexItem grow={{ default: 'grow' }}>
                              <FormGroup label="Enable from" fieldId="enable-from">
                                <TextInput
                                  id="enable-from"
                                  type="time"
                                  value={formEnableFrom}
                                  onChange={(_event, value) => setFormEnableFrom(value)}
                                  isDisabled={!formEnabled}
                                  aria-label="Enable from time"
                                />
                              </FormGroup>
                            </FlexItem>
                          </Flex>
                        )}

                        {/* Strategy selector */}
                        <FormGroup label="Strategy" fieldId="strategy-select">
                          <Select
                            id="strategy-select"
                            isOpen={strategyOpen}
                            onSelect={(_event, value) => {
                              setFormStrategy(value);
                              setStrategyOpen(false);
                            }}
                            onOpenChange={(isOpen) => setStrategyOpen(isOpen)}
                            toggle={(toggleRef) => (
                              <MenuToggle
                                ref={toggleRef}
                                onClick={() => setStrategyOpen(!strategyOpen)}
                                isExpanded={strategyOpen}
                                isDisabled={!formEnabled}
                                style={{ width: '100%' }}
                              >
                                {STRATEGY_LABELS[formStrategy] || formStrategy}
                              </MenuToggle>
                            )}
                          >
                            <SelectList>
                              <SelectOption value="ZERO_EXPORT_DYNAMIC">
                                Zero-export dynamic (Solar API)
                              </SelectOption>
                              <SelectOption value="FIXED_LIMIT">
                                Hard block (fixed watt cap)
                              </SelectOption>
                              <SelectOption value="PRICE_CONTROLLED">
                                Price-controlled (aWATTar AT)
                              </SelectOption>
                            </SelectList>
                          </Select>
                        </FormGroup>

                        {/* Fixed watt cap — only for FIXED_LIMIT */}
                        {formStrategy === 'FIXED_LIMIT' && (
                          <FormGroup label="Power cap (W)" fieldId="limit-watts">
                            <TextInput
                              id="limit-watts"
                              type="number"
                              value={String(formLimitWatts)}
                              onChange={(_event, value) => setFormLimitWatts(value)}
                              isDisabled={!formEnabled}
                              min={1}
                              step={100}
                              aria-label="Power cap in watts"
                            />
                            <FormHelperText>
                              <HelperText>
                                <HelperTextItem>
                                  Max inverter output during the block window (default: 500 W)
                                </HelperTextItem>
                              </HelperText>
                            </FormHelperText>
                          </FormGroup>
                        )}

                        {/* Export tolerance — only for PRICE_CONTROLLED */}
                        {formStrategy === 'PRICE_CONTROLLED' && (
                          <FormGroup label="Export tolerance (W)" fieldId="tolerance-watts">
                            <TextInput
                              id="tolerance-watts"
                              type="number"
                              value={String(formToleranceWatts)}
                              onChange={(_event, value) => setFormToleranceWatts(value)}
                              isDisabled={!formEnabled}
                              min={0}
                              step={10}
                              aria-label="Export tolerance in watts"
                            />
                            <FormHelperText>
                              <HelperText>
                                <HelperTextItem>
                                  Allowed grid export above load demand when price is negative (default: 50 W)
                                </HelperTextItem>
                              </HelperText>
                            </FormHelperText>
                          </FormGroup>
                        )}

                        {/* Current market price — only for PRICE_CONTROLLED */}
                        {formStrategy === 'PRICE_CONTROLLED' && (
                          <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
                            <FlexItem>
                              <span style={{ fontSize: '0.875rem', color: C.subtle }}>Current price</span>
                            </FlexItem>
                            <FlexItem>
                              {marketPriceQuery.isLoading ? (
                                <Spinner size="sm" />
                              ) : currentPrice == null ? (
                                <span style={{ fontSize: '0.75rem', color: C.disabled }}>not available</span>
                              ) : (
                                <Tooltip content={`Valid ${currentPrice.startTime} – ${currentPrice.endTime}`}>
                                  <span
                                    style={{
                                      fontSize: '0.875rem',
                                      fontFamily: 'monospace',
                                      fontWeight: 700,
                                      color: currentPrice.priceCt < 0 ? C.warning : C.success,
                                    }}
                                  >
                                    {currentPrice.priceCt.toFixed(2)} ct/kWh
                                    {currentPrice.priceCt < 0 ? ' (blocking)' : ' (normal)'}
                                  </span>
                                </Tooltip>
                              )}
                            </FlexItem>
                          </Flex>
                        )}

                        {/* Summary */}
                        <span style={{ fontSize: '0.75rem', color: C.subtle }}>
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
                        </span>

                        {/* Action buttons */}
                        <Flex gap={{ default: 'gapSm' }} justifyContent={{ default: 'justifyContentFlexEnd' }}>
                          {existingSchedule && (
                            <FlexItem>
                              <Tooltip content="Delete schedule">
                                <Button
                                  variant="plain"
                                  size="sm"
                                  onClick={handleDeleteSchedule}
                                  isDisabled={isDeleting || isSaving}
                                  aria-label="Delete schedule"
                                  style={{ color: C.danger }}
                                >
                                  {isDeleting ? <Spinner size="sm" /> : <TrashIcon />}
                                </Button>
                              </Tooltip>
                            </FlexItem>
                          )}
                          <FlexItem>
                            <Button
                              variant="primary"
                              size="sm"
                              onClick={handleSaveSchedule}
                              isDisabled={isSaving || isDeleting}
                              icon={isSaving ? <Spinner size="sm" /> : undefined}
                            >
                              Save
                            </Button>
                          </FlexItem>
                        </Flex>
                      </>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function MetricRow({ label, value }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <span style={{ fontSize: '0.875rem', color: C.subtle }}>{label}</span>
      <span style={{ fontSize: '0.875rem', fontWeight: 500, fontFamily: 'monospace' }}>{value}</span>
    </div>
  );
}

export default GridStatusCard;
