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
import { formatForDisplay } from '../../utils/timeZone';
import {
  Card,
  CardBody,
  Skeleton,
  Tooltip,
  Switch,
  Alert,
  Spinner,
  TextInput,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Button,
  Divider,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import {
  ChartLineIcon,
  BanIcon,
  AngleDownIcon,
  AngleUpIcon,
  TagIcon,
  SaveIcon,
} from '@patternfly/react-icons';
import { useSolarApiStatus } from '../../hooks/useSolarApi';
import {
  useSunSpecControls,
  useSetPowerLimit,
  useCurrentMarketPrice,
} from '../../hooks/useSunSpec';
import { usePriceControl, useSetPriceControl } from '../../hooks/usePriceControl';

// PF v6 design token CSS variable references
const C = {
  primary:  'var(--pf-t--global--color--brand--default, #0066cc)',
  success:  'var(--pf-t--global--color--status--success--default, #3e8635)',
  warning:  'var(--pf-t--global--color--status--warning--default, #f0ab00)',
  danger:   'var(--pf-t--global--color--status--danger--default, #c9190b)',
  subtle:   'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text-color--disabled, #b8bbbe)',
};

function formatPower(value) {
  if (value == null) return '-';
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';
  if (Math.abs(num) >= 1000) return `${(num / 1000).toFixed(2)} kW`;
  return `${num.toFixed(0)} W`;
}

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

  const gridColor = gridW < 0 ? C.success : gridW > 0 ? C.warning : null;
  const loadColor = loadW < 0 ? C.danger : null;

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        {/* Header */}
        <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }} style={{ marginBottom: '1rem' }}>
          <FlexItem>
            <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>
                <ChartLineIcon style={{ color: C.primary }} />
              </FlexItem>
              <FlexItem>
                <span style={{ fontWeight: 600, color: C.primary }}>Site Power Flow</span>
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
                <span style={{ fontSize: '0.75rem', fontWeight: 600, color: exportBlocked ? C.warning : C.subtle }}>
                  {exportBlocked ? 'Blocked' : 'Active'}
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
          <span style={{ fontSize: '0.875rem', color: C.subtle }}>Solar API unavailable</span>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            {/* Power flow values */}
            <MetricRow label="Grid" value={formatPower(gridW)} color={gridColor} />
            <MetricRow label="Load" value={formatPower(loadW)} color={loadColor} />
            <MetricRow label="PV" value={formatPower(pvW)} color={C.success} />
            <MetricRow
              label="Battery"
              value={formatPower(battW)}
              color={battW > 0 ? C.warning : battW < 0 ? C.success : null}
            />

            {/* Site Statistics */}
            {(autonomy != null || selfConsumption != null) && (
              <div style={{ marginTop: '0.5rem' }}>
                <Divider style={{ marginBottom: '0.75rem' }} />
                <span style={{ fontSize: '0.75rem', color: C.subtle, display: 'block', marginBottom: '0.25rem' }}>
                  Site Statistics
                </span>
                <MetricRow label="Autonomy" value={formatPercent(autonomy)} />
                <MetricRow label="Self-consumption" value={formatPercent(selfConsumption)} />
              </div>
            )}

            {/* Block Export Control */}
            {hasControls && (
              <div style={{ marginTop: '0.5rem' }}>
                <Divider style={{ marginBottom: '0.75rem' }} />
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
              </div>
            )}

            {/* Price Control Section */}
            <div style={{ marginTop: '0.5rem' }}>
              <Divider style={{ marginBottom: '0.75rem' }} />
              {/* Collapsible header */}
              <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
                <FlexItem>
                  <Flex gap={{ default: 'gapXs' }} alignItems={{ default: 'alignItemsCenter' }}>
                    <FlexItem>
                      <TagIcon style={{ fontSize: 16, color: pcEnabled ? C.primary : C.disabled }} />
                    </FlexItem>
                    <FlexItem>
                      <span style={{ fontSize: '0.875rem', color: C.subtle }}>Price control</span>
                    </FlexItem>
                    {pcCurrentlyBlocking && (
                      <FlexItem>
                        <Tooltip content="Price control is currently limiting export">
                          <span style={{ fontSize: '0.75rem', color: C.warning, fontWeight: 600 }}>blocking</span>
                        </Tooltip>
                      </FlexItem>
                    )}
                    {pcEnabled && !pcCurrentlyBlocking && (
                      <FlexItem>
                        <span style={{ fontSize: '0.75rem', color: C.disabled }}>enabled</span>
                      </FlexItem>
                    )}
                  </Flex>
                </FlexItem>
                <FlexItem>
                  <Button
                    variant="plain"
                    size="sm"
                    onClick={() => setPcOpen((o) => !o)}
                    aria-label={pcOpen ? 'Collapse price control' : 'Expand price control'}
                  >
                    {pcOpen ? <AngleUpIcon /> : <AngleDownIcon />}
                  </Button>
                </FlexItem>
              </Flex>

              {/* Collapsible form */}
              {pcOpen && (
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
                  {priceControlQuery.isLoading ? (
                    <div style={{ display: 'flex', justifyContent: 'center', padding: '0.5rem' }}>
                      <Spinner size="md" />
                    </div>
                  ) : (
                    <>
                      {/* Enable toggle */}
                      <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
                        <FlexItem>
                          <span style={{ fontSize: '0.875rem' }}>Enable price control</span>
                        </FlexItem>
                        <FlexItem>
                          <Switch
                            id="pc-enabled-switch"
                            isChecked={formPcEnabled}
                            onChange={(_event, checked) => setFormPcEnabled(checked)}
                            hasCheckIcon
                          />
                        </FlexItem>
                      </Flex>

                      {/* Tolerance input */}
                      <FormGroup label="Export tolerance (W)" fieldId="pc-tolerance">
                        <TextInput
                          id="pc-tolerance"
                          type="number"
                          value={String(formPcTolerance)}
                          onChange={(_event, value) => setFormPcTolerance(value)}
                          isDisabled={!formPcEnabled}
                          aria-label="Export tolerance in watts"
                          min={0}
                          step={10}
                        />
                        <FormHelperText>
                          <HelperText>
                            <HelperTextItem>
                              Allowed export above load+battery demand when price is negative (default: 50 W)
                            </HelperTextItem>
                          </HelperText>
                        </FormHelperText>
                      </FormGroup>

                      {/* Current market price */}
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
                            <Tooltip content={`Valid ${formatForDisplay(currentPrice.startTime)} – ${formatForDisplay(currentPrice.endTime)}`}>
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

                      {/* Summary */}
                      <span style={{ fontSize: '0.75rem', color: C.subtle }}>
                        {formPcEnabled
                          ? <>When aWATTar AT price is negative, export is capped to{' '}
                              <strong>{Math.max(0, parseInt(String(formPcTolerance), 10) || 50)} W</strong> above load demand.</>
                          : 'Price-controlled export limiting is disabled.'}
                      </span>

                      {/* Save button */}
                      <Flex justifyContent={{ default: 'justifyContentFlexEnd' }}>
                        <FlexItem>
                          <Tooltip content="Save price control setting">
                            <Button
                              variant="plain"
                              size="sm"
                              onClick={handleSavePc}
                              isDisabled={isSavingPc}
                              aria-label="Save price control"
                            >
                              {isSavingPc ? <Spinner size="sm" /> : <SaveIcon />}
                            </Button>
                          </Tooltip>
                        </FlexItem>
                      </Flex>
                    </>
                  )}
                </div>
              )}
            </div>

          </div>
        )}
      </CardBody>
    </Card>
  );
}

function MetricRow({ label, value, color }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <span style={{ fontSize: '0.875rem', color: C.subtle }}>{label}</span>
      <span style={{ fontSize: '0.875rem', fontWeight: 500, fontFamily: 'monospace', color: color || undefined }}>
        {value}
      </span>
    </div>
  );
}

export default SitePowerFlowCard;
