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
  Progress,
  ProgressSize,
  ProgressVariant,
  Divider,
} from '@patternfly/react-core';
import { BoltIcon, ExclamationTriangleIcon } from '@patternfly/react-icons';

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
 * Maps charge status enum to display label and color
 */
function getChargeStatus(chaSt) {
  if (chaSt == null) return { label: 'Unknown', color: C.disabled };
  const map = {
    1: { label: 'Off',          color: C.disabled },
    2: { label: 'Empty',        color: C.danger },
    3: { label: 'Discharging',  color: C.warning },
    4: { label: 'Charging',     color: C.success },
    5: { label: 'Full',         color: C.success },
    6: { label: 'Holding',      color: C.info },
    7: { label: 'Testing',      color: C.info },
  };
  return map[chaSt] || { label: `Status ${chaSt}`, color: C.subtle };
}

/**
 * Returns the progress bar variant based on SoC level
 */
function getSocVariant(soc) {
  if (soc == null) return ProgressVariant.danger;
  if (soc >= 60) return ProgressVariant.success;
  if (soc >= 20) return ProgressVariant.warning;
  return ProgressVariant.danger;
}

/**
 * BatteryStatusCard - shows battery state of charge, voltage, current, status
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
      <Card style={{ height: '100%' }}>
        <CardBody>
          <span style={{ fontWeight: 600, color: C.primary, display: 'block', marginBottom: '0.5rem' }}>Battery</span>
          <Alert variant="info" isInline title="No battery/storage system detected on this device." />
        </CardBody>
      </Card>
    );
  }

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }} style={{ marginBottom: '1rem' }}>
          <FlexItem>
            <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>
                <BoltIcon style={{ color: isCharging ? C.success : C.disabled }} />
              </FlexItem>
              <FlexItem>
                <span style={{ fontWeight: 600, color: C.primary }}>Battery</span>
              </FlexItem>
            </Flex>
          </FlexItem>
          <FlexItem>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: chargeStatus.color }}>
              {chargeStatus.label}
            </span>
          </FlexItem>
        </Flex>

        {isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <Skeleton height="1.25rem" />
            {[...Array(3)].map((_, i) => <Skeleton key={i} height="1.5rem" />)}
          </div>
        ) : isError ? (
          <Alert variant="warning" isInline title="Unable to read battery data" />
        ) : (
          <div>
            {soc != null && (
              <div style={{ marginBottom: '1rem' }}>
                <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} style={{ marginBottom: '0.25rem' }}>
                  <FlexItem>
                    <span style={{ fontSize: '0.875rem', color: C.subtle }}>State of Charge</span>
                  </FlexItem>
                  <FlexItem>
                    <span style={{ fontWeight: 700, fontFamily: 'monospace' }}>{soc.toFixed(1)}%</span>
                  </FlexItem>
                </Flex>
                <Progress
                  value={Math.min(soc, 100)}
                  variant={getSocVariant(soc)}
                  size={ProgressSize.sm}
                  aria-label="Battery state of charge"
                />
              </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              {batteryV != null && (
                <MetricRow label="Battery Voltage" value={`${batteryV.toFixed(1)} V`} />
              )}
              {storageAvailable != null && (
                <MetricRow label="Available Storage" value={`${storageAvailable.toFixed(1)} Ah`} />
              )}
              {minReserve != null && (
                <MetricRow label="Min. Reserve" value={`${minReserve.toFixed(1)}%`} />
              )}
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function MetricRow({ label, value }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <span style={{ fontSize: '0.875rem', color: 'var(--pf-t--global--text-color--subtle, #6a6e73)' }}>{label}</span>
      <span style={{ fontSize: '0.875rem', fontWeight: 500, fontFamily: 'monospace' }}>{value}</span>
    </div>
  );
}

export default BatteryStatusCard;
