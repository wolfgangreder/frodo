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
  Label,
  Button,
  Tooltip,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { Table, Thead, Tbody, Tr, Th, Td } from '@patternfly/react-table';
import {
  PencilAltIcon,
  TrashIcon,
  InfoCircleIcon,
  SyncAltIcon,
  ChartLineIcon,
  TachometerAltIcon,
} from '@patternfly/react-icons';
import { StatusChip } from '../common';
import DeviceCard from './DeviceCard';

const C = {
  primary:  'var(--pf-t--global--color--brand--default, #0066cc)',
  success:  'var(--pf-t--global--color--status--success--default, #3e8635)',
  warning:  'var(--pf-t--global--color--status--warning--default, #f0ab00)',
  danger:   'var(--pf-t--global--color--status--danger--default, #c9190b)',
  info:     'var(--pf-t--global--color--status--info--default, #0066cc)',
  subtle:   'var(--pf-t--global--text-color--subtle, #6a6e73)',
};

/**
 * Simple responsive hook — replaces MUI useMediaQuery
 */
function useIsMobile(breakpoint = 768) {
  const [isMobile, setIsMobile] = useState(() =>
    typeof window !== 'undefined' && window.innerWidth < breakpoint
  );
  useEffect(() => {
    const mq = window.matchMedia(`(max-width: ${breakpoint - 1}px)`);
    const handler = (e) => setIsMobile(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, [breakpoint]);
  return isMobile;
}

/**
 * Enabled label chip
 */
function EnabledChip({ enabled }) {
  return (
    <Label color={enabled ? 'blue' : 'grey'} variant={enabled ? 'filled' : 'outline'}>
      {enabled ? 'Enabled' : 'Disabled'}
    </Label>
  );
}

/**
 * Device list component - displays devices in table (desktop) or cards (mobile)
 *
 * @param {Object} props
 * @param {Array} props.devices - List of devices to display
 * @param {Function} props.onEdit - Callback when edit is clicked
 * @param {Function} props.onDelete - Callback when delete is clicked
 * @param {Function} props.onViewInfo - Callback when view info is clicked
 * @param {Function} props.onRefreshInfo - Callback when refresh info is clicked
 * @param {Function} props.onMetrics - Callback when metrics is clicked
 * @param {Function} props.onDashboard - Callback when dashboard is clicked
 * @param {boolean} props.isRefreshing - Whether info is being refreshed
 */
function DeviceList({
  devices = [],
  onEdit,
  onDelete,
  onViewInfo,
  onRefreshInfo,
  onMetrics,
  onDashboard,
  isRefreshing = false,
}) {
  const isMobile = useIsMobile();

  // Mobile view — cards
  if (isMobile) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {devices.map((device) => (
          <DeviceCard
            key={device.id}
            device={device}
            onEdit={onEdit}
            onDelete={onDelete}
            onViewInfo={onViewInfo}
            onRefreshInfo={onRefreshInfo}
            onMetrics={onMetrics}
            onDashboard={onDashboard}
            isRefreshing={isRefreshing}
          />
        ))}
      </div>
    );
  }

  // Desktop view — table
  return (
    <Table aria-label="Devices" variant="compact">
      <Thead>
        <Tr>
          <Th>Name</Th>
          <Th>Host</Th>
          <Th modifier="fitContent">Port</Th>
          <Th modifier="fitContent">Unit ID</Th>
          <Th modifier="fitContent">Status</Th>
          <Th modifier="fitContent">Enabled</Th>
          <Th modifier="fitContent" aria-label="Actions" />
        </Tr>
      </Thead>
      <Tbody>
        {devices.map((device) => (
          <Tr key={device.id}>
            <Td dataLabel="Name">{device.name}</Td>
            <Td dataLabel="Host">{device.host}</Td>
            <Td dataLabel="Port" modifier="fitContent">{device.port}</Td>
            <Td dataLabel="Unit ID" modifier="fitContent">{device.unitId}</Td>
            <Td dataLabel="Status" modifier="fitContent">
              <StatusChip status={device.connectionStatus} />
            </Td>
            <Td dataLabel="Enabled" modifier="fitContent">
              <EnabledChip enabled={device.enabled} />
            </Td>
            <Td dataLabel="Actions" modifier="fitContent">
              <Flex gap={{ default: 'gapXs' }} justifyContent={{ default: 'justifyContentFlexEnd' }}>
                <FlexItem>
                  <Tooltip content="Device Dashboard">
                    <Button variant="plain" size="sm" onClick={() => onDashboard?.(device)} aria-label={`Open dashboard for ${device.name}`}>
                      <TachometerAltIcon style={{ color: C.success }} />
                    </Button>
                  </Tooltip>
                </FlexItem>
                <FlexItem>
                  <Tooltip content="Metrics Configuration">
                    <Button variant="plain" size="sm" onClick={() => onMetrics?.(device)} aria-label={`Configure metrics for ${device.name}`}>
                      <ChartLineIcon style={{ color: C.warning }} />
                    </Button>
                  </Tooltip>
                </FlexItem>
                <FlexItem>
                  <Tooltip content="View Device Info">
                    <Button variant="plain" size="sm" onClick={() => onViewInfo?.(device)} aria-label={`View info for ${device.name}`}>
                      <InfoCircleIcon style={{ color: C.info }} />
                    </Button>
                  </Tooltip>
                </FlexItem>
                <FlexItem>
                  <Tooltip content="Refresh Device Info">
                    <Button variant="plain" size="sm" onClick={() => onRefreshInfo?.(device)} isDisabled={isRefreshing} aria-label={`Refresh info for ${device.name}`}>
                      <SyncAltIcon style={{ color: C.subtle }} />
                    </Button>
                  </Tooltip>
                </FlexItem>
                <FlexItem>
                  <Tooltip content="Edit Device">
                    <Button variant="plain" size="sm" onClick={() => onEdit?.(device)} aria-label={`Edit ${device.name}`}>
                      <PencilAltIcon style={{ color: C.primary }} />
                    </Button>
                  </Tooltip>
                </FlexItem>
                <FlexItem>
                  <Tooltip content="Delete Device">
                    <Button variant="plain" size="sm" onClick={() => onDelete?.(device)} aria-label={`Delete ${device.name}`}>
                      <TrashIcon style={{ color: C.danger }} />
                    </Button>
                  </Tooltip>
                </FlexItem>
              </Flex>
            </Td>
          </Tr>
        ))}
      </Tbody>
    </Table>
  );
}

export default DeviceList;
