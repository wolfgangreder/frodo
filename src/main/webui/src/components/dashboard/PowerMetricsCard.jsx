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

import React, { useMemo } from 'react';
import {
  Card,
  CardBody,
  Alert,
  Skeleton,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { BoltIcon } from '@patternfly/react-icons';

// PF v6 design token CSS variable references
const C = {
  primary:   'var(--pf-t--global--color--brand--default, #0066cc)',
  success:   'var(--pf-t--global--color--status--success--default, #3e8635)',
  warning:   'var(--pf-t--global--color--status--warning--default, #f0ab00)',
  danger:    'var(--pf-t--global--color--status--danger--default, #c9190b)',
  info:      'var(--pf-t--global--color--status--info--default, #0066cc)',
  subtle:    'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled:  'var(--pf-t--global--text-color--disabled, #b8bbbe)',
};

/**
 * Formats a numeric value with appropriate decimal places and unit
 */
function formatValue(value, unit, decimals = 1) {
  if (value == null || value === '' || (typeof value === 'number' && isNaN(value))) {
    return '-';
  }
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (isNaN(num)) return '-';

  if (unit === 'Wh' && Math.abs(num) >= 1000000) return `${(num / 1000000).toFixed(1)} MWh`;
  if (unit === 'Wh' && Math.abs(num) >= 1000) return `${(num / 1000).toFixed(1)} kWh`;
  if (unit === 'W' && Math.abs(num) >= 1000) return `${(num / 1000).toFixed(decimals)} kW`;

  return `${num.toFixed(decimals)} ${unit}`;
}

/**
 * Maps inverter operating state enum to display label and color
 */
function getOperatingState(st) {
  if (st == null) return { label: 'Unknown', color: C.disabled };
  const stateMap = {
    1: { label: 'Off',              color: C.disabled },
    2: { label: 'Sleeping',         color: C.warning },
    3: { label: 'Starting',         color: C.warning },
    4: { label: 'Running (MPPT)',    color: C.success },
    5: { label: 'Throttled',        color: C.warning },
    6: { label: 'Shutting Down',    color: C.warning },
    7: { label: 'Fault',            color: C.danger },
    8: { label: 'Standby',          color: C.info },
  };
  return stateMap[st] || { label: `State ${st}`, color: C.subtle };
}

/**
 * PowerMetricsCard - displays real-time AC/DC power, voltage, current, frequency, energy
 */
function PowerMetricsCard({ inverterData, isLoading, isError }) {
  const fields = inverterData?.fields || {};
  const state = useMemo(() => getOperatingState(fields.St), [fields.St]);
  const isGenerating = fields.St === 4 && fields.W != null && fields.W > 0;

  const primaryMetrics = [
    { label: 'AC Power',     value: fields.W,    unit: 'W',  decimals: 0 },
    { label: 'Energy Total', value: fields.WH,   unit: 'Wh', decimals: 0 },
  ];

  const secondaryMetrics = [
    { label: 'AC Voltage',    value: fields.PhVphA, unit: 'V',   decimals: 1 },
    { label: 'AC Current',    value: fields.A,      unit: 'A',   decimals: 2 },
    { label: 'Frequency',     value: fields.Hz,     unit: 'Hz',  decimals: 2 },
    { label: 'Power Factor',  value: fields.PF,     unit: 'cos(\u03C6)',   decimals: 2 },
  ];

  const dcMetrics = [
    { label: 'DC Voltage', value: fields.DCV, unit: 'V', decimals: 1 },
    { label: 'DC Current', value: fields.DCA, unit: 'A', decimals: 2 },
    { label: 'DC Power',   value: fields.DCW, unit: 'W', decimals: 0 },
  ];

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }} style={{ marginBottom: '1rem' }}>
          <FlexItem>
            <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>
                <BoltIcon style={{ color: isGenerating ? C.success : C.disabled }} />
              </FlexItem>
              <FlexItem>
                <span style={{ fontWeight: 600, color: C.primary }}>Power</span>
              </FlexItem>
            </Flex>
          </FlexItem>
          <FlexItem>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: state.color }}>
              {state.label}
            </span>
          </FlexItem>
        </Flex>

        {isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {[...Array(6)].map((_, i) => <Skeleton key={i} height="1.5rem" />)}
          </div>
        ) : isError ? (
          <Alert variant="warning" isInline title="Unable to read inverter data" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            {primaryMetrics.map((m) => (
              <MetricRow key={m.label} label={m.label} value={formatValue(m.value, m.unit, m.decimals)} primary />
            ))}
            {secondaryMetrics.map((m) => (
              <MetricRow key={m.label} label={m.label} value={formatValue(m.value, m.unit, m.decimals)} />
            ))}
            {dcMetrics.some((m) => m.value != null) && (
              <>
                <span style={{ fontSize: '0.75rem', color: C.subtle, marginTop: '0.5rem', marginBottom: '0.25rem' }}>
                  DC Side
                </span>
                {dcMetrics.map((m) => (
                  <MetricRow key={m.label} label={m.label} value={formatValue(m.value, m.unit, m.decimals)} />
                ))}
              </>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function MetricRow({ label, value, primary = false }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <span style={{ fontSize: '0.875rem', color: 'var(--pf-t--global--text-color--subtle, #6a6e73)' }}>{label}</span>
      <span style={{
        fontSize: primary ? '1rem' : '0.875rem',
        fontWeight: primary ? 700 : 500,
        fontFamily: 'monospace',
      }}>
        {value}
      </span>
    </div>
  );
}

export default PowerMetricsCard;
