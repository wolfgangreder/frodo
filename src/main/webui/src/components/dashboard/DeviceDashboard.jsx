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
  Grid,
  GridItem,
  Alert,
  Button,
  Label,
  Tooltip,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { SyncAltIcon } from '@patternfly/react-icons';
import { useQueryClient } from '@tanstack/react-query';
import {
  useSunSpecCommon,
  useSunSpecInverter,
  useSunSpecStorage,
  useSunSpecStatus,
  useSunSpecDiscovery,
  sunspecKeys,
} from '../../hooks/useSunSpec';
import DeviceStatusCard from './DeviceStatusCard';
import PowerMetricsCard from './PowerMetricsCard';
import BatteryStatusCard from './BatteryStatusCard';
import GridStatusCard from './GridStatusCard';
import SitePowerFlowCard from './SitePowerFlowCard';

/**
 * Formats a relative time string from an ISO timestamp
 */
function formatTimeAgo(timestamp) {
  if (!timestamp) return null;
  const now = Date.now();
  const then = new Date(timestamp).getTime();
  const diffSec = Math.floor((now - then) / 1000);
  if (diffSec < 10) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  return `${Math.floor(diffSec / 3600)}h ago`;
}

/**
 * DeviceDashboard - orchestrates all dashboard cards for a single device.
 */
function DeviceDashboard({ device }) {
  const queryClient = useQueryClient();
  const deviceId = device?.id;

  const discoveryQuery = useSunSpecDiscovery(deviceId);
  const discovery = discoveryQuery.data;
  const isOffline = discoveryQuery.isError;
  const isDiscovering = discoveryQuery.isLoading;

  const hasStorage = useMemo(() => {
    if (!discovery?.models) return false;
    return discovery.models.some((m) => m.modelId === 124);
  }, [discovery]);

  const hasStatus = useMemo(() => {
    if (!discovery?.models) return false;
    return discovery.models.some((m) => m.modelId === 122);
  }, [discovery]);

  const hasControls = useMemo(() => {
    if (!discovery?.models) return false;
    return discovery.models.some((m) => m.modelId === 123);
  }, [discovery]);

  const onlineAndReady = !!deviceId && discoveryQuery.isSuccess;

  const commonQuery = useSunSpecCommon(deviceId, { enabled: onlineAndReady });
  const inverterQuery = useSunSpecInverter(deviceId, { enabled: onlineAndReady });
  const storageQuery = useSunSpecStorage(deviceId, { enabled: onlineAndReady && hasStorage });
  const statusQuery = useSunSpecStatus(deviceId, { enabled: onlineAndReady && hasStatus });

  const lastUpdate = inverterQuery.data?.readTime || commonQuery.data?.readTime;

  const handleRefreshAll = () => {
    queryClient.invalidateQueries({ queryKey: sunspecKeys.discovery(deviceId) });
    if (onlineAndReady) {
      queryClient.invalidateQueries({ queryKey: sunspecKeys.common(deviceId) });
      queryClient.invalidateQueries({ queryKey: sunspecKeys.inverter(deviceId) });
      if (hasStorage) queryClient.invalidateQueries({ queryKey: sunspecKeys.storage(deviceId) });
      if (hasStatus) queryClient.invalidateQueries({ queryKey: sunspecKeys.status(deviceId) });
    }
  };

  const isAnyFetching =
    discoveryQuery.isFetching ||
    commonQuery.isFetching ||
    inverterQuery.isFetching ||
    storageQuery.isFetching ||
    statusQuery.isFetching;

  return (
    <div>
      {/* Toolbar */}
      <Flex
        justifyContent={{ default: 'justifyContentFlexEnd' }}
        alignItems={{ default: 'alignItemsCenter' }}
        gap={{ default: 'gapSm' }}
        style={{ marginBottom: '0.5rem' }}
      >
        {lastUpdate && !isOffline && (
          <FlexItem>
            <Label
              variant="outline"
              color={inverterQuery.isError ? 'red' : 'grey'}
            >
              Updated {formatTimeAgo(lastUpdate)}
            </Label>
          </FlexItem>
        )}
        {isOffline && (
          <FlexItem>
            <Label variant="outline" color="orange">
              Offline — retrying in 60 s
            </Label>
          </FlexItem>
        )}
        {!isOffline && (
          <FlexItem>
            <span style={{ fontSize: '0.75rem', color: 'var(--pf-t--global--text-color--subtle, #6a6e73)' }}>
              Auto-refresh: 10 s
            </span>
          </FlexItem>
        )}
        <FlexItem>
          <Tooltip content="Refresh all data">
            <Button
              variant="plain"
              onClick={handleRefreshAll}
              isDisabled={isAnyFetching}
              aria-label="Refresh all device data"
              style={isAnyFetching ? { animation: 'spin 1s linear infinite' } : undefined}
            >
              <SyncAltIcon />
            </Button>
          </Tooltip>
        </FlexItem>
      </Flex>

      <style>{`@keyframes spin { from { transform: rotate(0deg) } to { transform: rotate(360deg) } }`}</style>

      {/* Offline state */}
      {isOffline && (
        <div>
          <Alert
            variant="warning"
            isInline
            title="Modbus connection unavailable — SunSpec data cannot be read. Discovery retries automatically every 60 s."
            style={{ marginBottom: '1rem' }}
            actionLinks={
              <Button variant="link" isInline onClick={handleRefreshAll} isDisabled={discoveryQuery.isFetching}>
                Retry now
              </Button>
            }
          />
          <Grid hasGutter>
            <GridItem sm={6} lg={4}>
              <DeviceStatusCard device={device} commonData={null} inverterData={null} isLoading={false} isError />
            </GridItem>
          </Grid>
        </div>
      )}

      {/* Online / discovering state */}
      {!isOffline && (
        <Grid hasGutter>
          <GridItem sm={6} lg={3}>
            <DeviceStatusCard
              device={device}
              commonData={commonQuery.data}
              inverterData={inverterQuery.data}
              isLoading={isDiscovering || commonQuery.isLoading}
              isError={commonQuery.isError && inverterQuery.isError}
            />
          </GridItem>
          <GridItem sm={6} lg={3}>
            <PowerMetricsCard
              inverterData={inverterQuery.data}
              isLoading={isDiscovering || inverterQuery.isLoading}
              isError={inverterQuery.isError}
            />
          </GridItem>
          <GridItem sm={6} lg={3}>
            <BatteryStatusCard
              storageData={storageQuery.data}
              isLoading={(storageQuery.isLoading && hasStorage) || isDiscovering}
              isError={storageQuery.isError}
              hasStorage={hasStorage}
            />
          </GridItem>
          <GridItem sm={6} lg={3}>
            <SitePowerFlowCard
              deviceId={deviceId}
              statusData={statusQuery.data}
              hasControls={hasControls}
            />
          </GridItem>
          <GridItem sm={6} lg={3}>
            <GridStatusCard
              deviceId={deviceId}
              statusData={statusQuery.data}
              inverterData={inverterQuery.data}
              isLoading={(statusQuery.isLoading && hasStatus) || isDiscovering}
              isError={statusQuery.isError && hasStatus}
              hasControls={hasControls}
            />
          </GridItem>
        </Grid>
      )}
    </div>
  );
}

export default DeviceDashboard;
