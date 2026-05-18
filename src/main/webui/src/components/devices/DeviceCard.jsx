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
  CardFooter,
  Label,
  Button,
  Tooltip,
  Divider,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import {
  PencilAltIcon,
  TrashIcon,
  InfoCircleIcon,
  SyncAltIcon,
  ChartLineIcon,
  TachometerAltIcon,
  NetworkWiredIcon,
} from '@patternfly/react-icons';
import { StatusChip } from '../common';

// PF v6 design token CSS variable references
const C = {
  primary:  'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:   'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text-color--disabled, #b8bbbe)',
  danger:   'var(--pf-t--global--color--status--danger--default, #c9190b)',
  success:  'var(--pf-t--global--color--status--success--default, #3e8635)',
  warning:  'var(--pf-t--global--color--status--warning--default, #f0ab00)',
};

/**
 * Device card component for mobile view
 *
 * @param {Object} props
 * @param {Object} props.device - Device data
 * @param {Function} props.onEdit - Callback when edit is clicked
 * @param {Function} props.onDelete - Callback when delete is clicked
 * @param {Function} props.onViewInfo - Callback when view info is clicked
 * @param {Function} props.onRefreshInfo - Callback when refresh info is clicked
 * @param {Function} props.onMetrics - Callback when metrics is clicked
 * @param {Function} props.onDashboard - Callback when dashboard is clicked
 * @param {boolean} props.isRefreshing - Whether info is being refreshed
 */
function DeviceCard({
  device,
  onEdit,
  onDelete,
  onViewInfo,
  onRefreshInfo,
  onMetrics,
  onDashboard,
  isRefreshing = false,
}) {
  return (
    <Card style={{ opacity: device.enabled ? 1 : 0.7 }}>
      <CardBody>
        <Flex alignItems={{ default: 'alignItemsFlexStart' }} style={{ marginBottom: '1rem' }}>
          <FlexItem>
            <NetworkWiredIcon
              style={{
                fontSize: 40,
                color: device.enabled ? C.primary : C.disabled,
                marginRight: '0.75rem',
              }}
            />
          </FlexItem>
          <FlexItem grow={{ default: 'grow' }}>
            <div style={{ fontWeight: 600, fontSize: '1rem' }}>{device.name}</div>
            <div style={{ fontSize: '0.875rem', color: C.subtle }}>
              {device.host}:{device.port} (Unit {device.unitId})
            </div>
          </FlexItem>
          <FlexItem>
            <Flex flexDirection={{ default: 'column' }} gap={{ default: 'gapXs' }} alignItems={{ default: 'alignItemsFlexEnd' }}>
              <FlexItem><StatusChip status={device.connectionStatus} /></FlexItem>
              <FlexItem>
                <Label
                  color={device.enabled ? 'blue' : 'grey'}
                  variant={device.enabled ? 'filled' : 'outline'}
                >
                  {device.enabled ? 'Enabled' : 'Disabled'}
                </Label>
              </FlexItem>
            </Flex>
          </FlexItem>
        </Flex>

        {device.manufacturer && (
          <div style={{ marginTop: '0.25rem', fontSize: '0.75rem', color: C.subtle }}>
            {device.manufacturer}
            {device.modelName && ` - ${device.modelName}`}
          </div>
        )}
      </CardBody>

      <Divider />

      <CardFooter>
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
                <InfoCircleIcon style={{ color: 'var(--pf-t--global--color--status--info--default, #0066cc)' }} />
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
      </CardFooter>
    </Card>
  );
}

export default DeviceCard;
