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

import React from 'react';
import {
  Card,
  CardBody,
  Label,
  Skeleton,
  Flex,
  FlexItem,
  Divider,
} from '@patternfly/react-core';
import {
  CheckCircleIcon,
  QuestionCircleIcon,
} from '@patternfly/react-icons';

// PF v6 design token CSS variable references
const C = {
  primary:   'var(--pf-t--global--color--brand--default, #0066cc)',
  success:   'var(--pf-t--global--color--status--success--default, #3e8635)',
  warning:   'var(--pf-t--global--color--status--warning--default, #f0ab00)',
  subtle:    'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled:  'var(--pf-t--global--text-color--disabled, #b8bbbe)',
};

/**
 * Formats a relative time string from an ISO timestamp or Instant string
 */
function formatTimeAgo(timestamp) {
  if (!timestamp) return 'Never';
  const now = Date.now();
  const then = new Date(timestamp).getTime();
  const diffSec = Math.floor((now - then) / 1000);
  if (diffSec < 10) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

/**
 * DeviceStatusCard - shows device identity and connection status.
 */
function DeviceStatusCard({ device, commonData, inverterData, isLoading, isError }) {
  const fields = commonData?.fields || {};
  const manufacturer = fields.Mn || null;
  const model = fields.Md || null;
  const serial = fields.SN || null;
  const firmware = fields.Vr || null;
  const lastRead = inverterData?.readTime || commonData?.readTime || null;
  const isOnline = !!inverterData && !isError;

  const statusLabel = isLoading
    ? <Label variant="outline" color="grey">Connecting…</Label>
    : isError
      ? <Label variant="outline" color="orange">Offline</Label>
      : isOnline
        ? <Label variant="outline" color="green" icon={<CheckCircleIcon />}>Online</Label>
        : <Label variant="outline" color="grey" icon={<QuestionCircleIcon />}>Unknown</Label>;

  return (
    <Card style={{ height: '100%' }}>
      <CardBody>
        <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }} style={{ marginBottom: '1rem' }}>
          <FlexItem>
            <span style={{ fontWeight: 600, color: C.primary }}>Device Status</span>
          </FlexItem>
          <FlexItem>{statusLabel}</FlexItem>
        </Flex>

        {isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {[...Array(4)].map((_, i) => <Skeleton key={i} height="1.5rem" />)}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.375rem' }}>
            <InfoRow label="Name" value={device?.name} />
            <InfoRow label="Connection" value={`${device?.host}:${device?.port} unit ${device?.unitId}`} />

            {isError ? (
              <>
                <Divider style={{ margin: '0.25rem 0' }} />
                <span style={{ fontSize: '0.75rem', color: C.subtle, fontStyle: 'italic' }}>
                  Modbus unreachable — SunSpec data unavailable
                </span>
              </>
            ) : (
              <>
                {(manufacturer || model || serial || firmware) && (
                  <Divider style={{ margin: '0.25rem 0' }} />
                )}
                {manufacturer && <InfoRow label="Manufacturer" value={manufacturer} />}
                {model && <InfoRow label="Model" value={model} />}
                {serial && <InfoRow label="Serial" value={serial} />}
                {firmware && <InfoRow label="Firmware" value={firmware} />}
                <Divider style={{ margin: '0.25rem 0' }} />
                <InfoRow label="Last Read" value={formatTimeAgo(lastRead)} />
              </>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function InfoRow({ label, value }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: '0.5rem' }}>
      <span style={{ fontSize: '0.875rem', color: 'var(--pf-t--global--text-color--subtle, #6a6e73)' }}>{label}</span>
      <span style={{ fontSize: '0.875rem', fontWeight: 500, textAlign: 'right', maxWidth: '60%' }}>{value ?? '-'}</span>
    </div>
  );
}

export default DeviceStatusCard;
