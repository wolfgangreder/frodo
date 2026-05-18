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

import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@patternfly/react-core';
import { PlusIcon, NetworkWiredIcon } from '@patternfly/react-icons';
import { PageHeader, LoadingSpinner, ErrorDisplay, EmptyState as EmptyStateComponent } from '../components/common';
import {
  DeviceList,
  DeviceForm,
  DeviceDeleteDialog,
  DeviceInfoDialog,
} from '../components/devices';
import {
  useDevices,
  useDeviceInfo,
  useRefreshDeviceInfo,
} from '../hooks';

/**
 * Devices page - list and manage PV devices
 * Full CRUD operations with connection testing
 */
function DevicesPage() {
  const navigate = useNavigate();

  const {
    devices,
    isLoading,
    isError,
    error,
    refetch,
    createDevice,
    isCreating,
    updateDevice,
    isUpdating,
    deleteDevice,
    isDeleting,
    testConnection,
  } = useDevices();

  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [infoDialogOpen, setInfoDialogOpen] = useState(false);
  const [selectedDevice, setSelectedDevice] = useState(null);

  const {
    data: deviceInfo,
    isLoading: isInfoLoading,
  } = useDeviceInfo(selectedDevice?.id, {
    enabled: infoDialogOpen && !!selectedDevice?.id,
  });

  const refreshDeviceInfo = useRefreshDeviceInfo();

  const handleAddDevice = useCallback(() => {
    setSelectedDevice(null);
    setFormDialogOpen(true);
  }, []);

  const handleEditDevice = useCallback((device) => {
    setSelectedDevice(device);
    setFormDialogOpen(true);
  }, []);

  const handleFormClose = useCallback(() => {
    setFormDialogOpen(false);
    setSelectedDevice(null);
  }, []);

  const handleFormSubmit = useCallback(async (deviceData) => {
    try {
      if (selectedDevice) {
        await updateDevice({ id: selectedDevice.id, device: deviceData });
      } else {
        await createDevice(deviceData);
      }
      handleFormClose();
    } catch (err) {
      console.error('Form submission error:', err);
    }
  }, [selectedDevice, updateDevice, createDevice, handleFormClose]);

  const handleDeleteClick = useCallback((device) => {
    setSelectedDevice(device);
    setDeleteDialogOpen(true);
  }, []);

  const handleDeleteClose = useCallback(() => {
    setDeleteDialogOpen(false);
    setSelectedDevice(null);
  }, []);

  const handleDeleteConfirm = useCallback(async (deviceId) => {
    try {
      await deleteDevice(deviceId);
      handleDeleteClose();
    } catch (err) {
      console.error('Delete error:', err);
    }
  }, [deleteDevice, handleDeleteClose]);

  const handleMetrics = useCallback((device) => {
    navigate(`/devices/${device.id}/metrics`);
  }, [navigate]);

  const handleDashboard = useCallback((device) => {
    sessionStorage.setItem('dashboard.selectedDeviceId', String(device.id));
    navigate('/');
  }, [navigate]);

  const handleViewInfo = useCallback((device) => {
    setSelectedDevice(device);
    setInfoDialogOpen(true);
  }, []);

  const handleInfoClose = useCallback(() => {
    setInfoDialogOpen(false);
    setSelectedDevice(null);
  }, []);

  const handleRefreshInfo = useCallback(async (deviceId) => {
    try {
      await refreshDeviceInfo.mutateAsync(deviceId);
    } catch (err) {
      console.error('Refresh info error:', err);
    }
  }, [refreshDeviceInfo]);

  const addButton = (
    <Button
      variant="primary"
      icon={<PlusIcon />}
      onClick={handleAddDevice}
    >
      Add Device
    </Button>
  );

  if (isLoading) {
    return (
      <div>
        <PageHeader title="Devices" subtitle="Manage your PV modules and inverters" />
        <LoadingSpinner message="Loading devices..." />
      </div>
    );
  }

  if (isError) {
    return (
      <div>
        <PageHeader title="Devices" subtitle="Manage your PV modules and inverters" />
        <ErrorDisplay
          title="Failed to load devices"
          message={error?.message || 'An unknown error occurred'}
          onRetry={refetch}
        />
      </div>
    );
  }

  if (devices.length === 0) {
    return (
      <div>
        <PageHeader
          title="Devices"
          subtitle="Manage your PV modules and inverters"
          actions={addButton}
        />

        <EmptyStateComponent
          title="No devices configured yet"
          description="Add your first PV device to start monitoring."
          icon={<NetworkWiredIcon style={{ fontSize: 48, opacity: 0.5, width: 48, height: 48 }} />}
          actionLabel="Add Your First Device"
          onAction={handleAddDevice}
        />

        <DeviceForm
          open={formDialogOpen}
          onClose={handleFormClose}
          onSubmit={handleFormSubmit}
          device={selectedDevice}
          isSubmitting={isCreating || isUpdating}
          onTestConnection={testConnection}
        />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Devices"
        subtitle={`${devices.length} device${devices.length === 1 ? '' : 's'} configured`}
        actions={addButton}
      />

      <DeviceList
        devices={devices}
        onEdit={handleEditDevice}
        onDelete={handleDeleteClick}
        onViewInfo={handleViewInfo}
        onRefreshInfo={(device) => handleRefreshInfo(device.id)}
        onMetrics={handleMetrics}
        onDashboard={handleDashboard}
        isRefreshing={refreshDeviceInfo.isPending}
      />

      <DeviceForm
        open={formDialogOpen}
        onClose={handleFormClose}
        onSubmit={handleFormSubmit}
        device={selectedDevice}
        isSubmitting={isCreating || isUpdating}
        onTestConnection={testConnection}
      />

      <DeviceDeleteDialog
        open={deleteDialogOpen}
        onClose={handleDeleteClose}
        onConfirm={handleDeleteConfirm}
        device={selectedDevice}
        isDeleting={isDeleting}
      />

      <DeviceInfoDialog
        open={infoDialogOpen}
        onClose={handleInfoClose}
        onRefresh={handleRefreshInfo}
        device={selectedDevice}
        deviceInfo={deviceInfo?.identification ? { ...deviceInfo.identification, lastUpdated: deviceInfo.lastUpdated } : null}
        isLoading={isInfoLoading}
        isRefreshing={refreshDeviceInfo.isPending}
      />
    </div>
  );
}

export default DevicesPage;
