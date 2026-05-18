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
  Alert,
  Card,
  CardBody,
  Grid,
  GridItem,
  Label,
  MenuToggle,
  Select,
  SelectOption,
  Title,
} from '@patternfly/react-core';
import { NetworkWiredIcon } from '@patternfly/react-icons';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, LoadingSpinner, ErrorDisplay, EmptyStateComponent } from '../components/common';
import { DeviceDashboard } from '../components/dashboard';
import { useDeviceList } from '../hooks';
import { systemApi } from '../services';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #73bcf7)',
  info: 'var(--pf-t--global--icon--color--status--info--default, #2b9af3)',
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
};

/**
 * Dashboard page - main overview of PV system status
 */
function DashboardPage() {
  const navigate = useNavigate();

  const { data: appInfo } = useQuery({
    queryKey: ['appInfo'],
    queryFn: systemApi.getInfo,
  });

  const {
    data: devices,
    isLoading: isDevicesLoading,
    isError: isDevicesError,
    error: devicesError,
    refetch: refetchDevices,
  } = useDeviceList();

  const [selectedDeviceId, setSelectedDeviceId] = useState(() => {
    const stored = sessionStorage.getItem('dashboard.selectedDeviceId');
    return stored ? Number(stored) : null;
  });
  const [isSelectOpen, setIsSelectOpen] = useState(false);

  useEffect(() => {
    if (devices && devices.length > 0 && selectedDeviceId == null) {
      const enabledDevice = devices.find((d) => d.enabled) || devices[0];
      setSelectedDeviceId(enabledDevice.id);
    }
  }, [devices, selectedDeviceId]);

  useEffect(() => {
    if (selectedDeviceId != null) {
      sessionStorage.setItem('dashboard.selectedDeviceId', String(selectedDeviceId));
    }
  }, [selectedDeviceId]);

  const selectedDevice = devices?.find((d) => d.id === selectedDeviceId) || null;
  const selectedDeviceLabel = selectedDevice
    ? `${selectedDevice.name}${!selectedDevice.enabled ? ' (disabled)' : ''}`
    : 'Select device';

  if (isDevicesLoading) {
    return <LoadingSpinner message="Loading dashboard..." fullPage />;
  }

  if (isDevicesError) {
    return (
      <ErrorDisplay
        title="Failed to load dashboard"
        message={devicesError?.message}
        onRetry={refetchDevices}
        fullPage
      />
    );
  }

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle="Real-time PV system monitoring"
        actions={
          devices && devices.length > 1 ? (
            <Select
              isOpen={isSelectOpen}
              onOpenChange={setIsSelectOpen}
              toggle={(ref) => (
                <MenuToggle
                  ref={ref}
                  onClick={() => setIsSelectOpen(!isSelectOpen)}
                  isExpanded={isSelectOpen}
                  style={{ minWidth: 200 }}
                >
                  {selectedDeviceLabel}
                </MenuToggle>
              )}
              onSelect={(_e, value) => {
                setSelectedDeviceId(Number(value));
                setIsSelectOpen(false);
              }}
              selected={selectedDeviceId != null ? String(selectedDeviceId) : ''}
            >
              {(devices || []).map((d) => (
                <SelectOption key={d.id} value={String(d.id)}>
                  {d.name}{!d.enabled ? ' (disabled)' : ''}
                </SelectOption>
              ))}
            </Select>
          ) : null
        }
      />

      {(!devices || devices.length === 0) ? (
        <EmptyStateComponent
          title="No Devices Configured"
          description="Add a Modbus device to start monitoring your PV system."
          icon={<NetworkWiredIcon style={{ fontSize: 48, opacity: 0.5 }} />}
          actionLabel="Go to Devices"
          onAction={() => navigate('/devices')}
        />
      ) : !selectedDevice ? (
        <Alert variant="info" title="Select a device to view its dashboard." isInline style={{ marginTop: 16 }} />
      ) : !selectedDevice.enabled ? (
        <div>
          <Alert
            variant="warning"
            title="This device is disabled. Enable it in the device configuration to start monitoring."
            isInline
            style={{ marginBottom: 16 }}
          />
          <DeviceDashboard device={selectedDevice} />
        </div>
      ) : (
        <DeviceDashboard device={selectedDevice} />
      )}

      <Grid hasGutter style={{ marginTop: 24 }}>
        <GridItem span={12} sm={6} md={4}>
          <Card>
            <CardBody>
              <Title headingLevel="h3" size="md" style={{ color: C.primary, marginBottom: 8 }}>
                Application
              </Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: C.subtle, fontSize: '0.875rem' }}>Name</span>
                  <span style={{ fontSize: '0.875rem' }}>{appInfo?.name || 'Frodo'}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: C.subtle, fontSize: '0.875rem' }}>Version</span>
                  <Label color="blue">{appInfo?.version || '0.0.0'}</Label>
                </div>
              </div>
            </CardBody>
          </Card>
        </GridItem>
        <GridItem span={12} sm={6} md={4}>
          <Card>
            <CardBody>
              <Title headingLevel="h3" size="md" style={{ color: C.primary, marginBottom: 8 }}>
                Quick Links
              </Title>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {[
                  { href: '/swagger-ui', label: 'Swagger UI' },
                  { href: '/q/metrics', label: 'Prometheus Metrics' },
                  { href: '/q/health', label: 'Health Check' },
                ].map((link) => (
                  <a
                    key={link.href}
                    href={link.href}
                    target="_blank"
                    rel="noreferrer"
                    style={{ color: C.info, textDecoration: 'none', fontSize: '0.875rem' }}
                  >
                    {link.label}
                  </a>
                ))}
              </div>
            </CardBody>
          </Card>
        </GridItem>
      </Grid>
    </div>
  );
}

export default DashboardPage;
