import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RouterIcon from '@mui/icons-material/Router';
import { PageHeader, LoadingSpinner, ErrorDisplay, EmptyState } from '../components/common';
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

  // Device list and mutations
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

  // Dialog states
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [infoDialogOpen, setInfoDialogOpen] = useState(false);
  
  // Selected device for dialogs
  const [selectedDevice, setSelectedDevice] = useState(null);

  // Device info query (only when info dialog is open)
  const {
    data: deviceInfo,
    isLoading: isInfoLoading,
  } = useDeviceInfo(selectedDevice?.id, {
    enabled: infoDialogOpen && !!selectedDevice?.id,
  });

  const refreshDeviceInfo = useRefreshDeviceInfo();

  // ==================== Form Dialog Handlers ====================

  /**
   * Open form dialog for creating new device
   */
  const handleAddDevice = useCallback(() => {
    setSelectedDevice(null);
    setFormDialogOpen(true);
  }, []);

  /**
   * Open form dialog for editing existing device
   */
  const handleEditDevice = useCallback((device) => {
    setSelectedDevice(device);
    setFormDialogOpen(true);
  }, []);

  /**
   * Close form dialog
   */
  const handleFormClose = useCallback(() => {
    setFormDialogOpen(false);
    setSelectedDevice(null);
  }, []);

  /**
   * Handle form submission (create or update)
   */
  const handleFormSubmit = useCallback(async (deviceData) => {
    try {
      if (selectedDevice) {
        await updateDevice({ id: selectedDevice.id, device: deviceData });
      } else {
        await createDevice(deviceData);
      }
      handleFormClose();
    } catch (error) {
      // Error is handled by the mutation's onError
      console.error('Form submission error:', error);
    }
  }, [selectedDevice, updateDevice, createDevice, handleFormClose]);

  // ==================== Delete Dialog Handlers ====================

  /**
   * Open delete confirmation dialog
   */
  const handleDeleteClick = useCallback((device) => {
    setSelectedDevice(device);
    setDeleteDialogOpen(true);
  }, []);

  /**
   * Close delete dialog
   */
  const handleDeleteClose = useCallback(() => {
    setDeleteDialogOpen(false);
    setSelectedDevice(null);
  }, []);

  /**
   * Confirm device deletion
   */
  const handleDeleteConfirm = useCallback(async (deviceId) => {
    try {
      await deleteDevice(deviceId);
      handleDeleteClose();
    } catch (error) {
      // Error is handled by the mutation's onError
      console.error('Delete error:', error);
    }
  }, [deleteDevice, handleDeleteClose]);

  // ==================== Info Dialog Handlers ====================

  /**
   * Navigate to metrics configuration for a device
   */
  const handleMetrics = useCallback((device) => {
    navigate(`/devices/${device.id}/metrics`);
  }, [navigate]);

  /**
   * Navigate to dashboard for a device
   */
  const handleDashboard = useCallback((device) => {
    // Set the device in sessionStorage so the dashboard page picks it up
    sessionStorage.setItem('dashboard.selectedDeviceId', String(device.id));
    navigate('/');
  }, [navigate]);

  /**
   * Open device info dialog
   */
  const handleViewInfo = useCallback((device) => {
    setSelectedDevice(device);
    setInfoDialogOpen(true);
  }, []);

  /**
   * Close info dialog
   */
  const handleInfoClose = useCallback(() => {
    setInfoDialogOpen(false);
    setSelectedDevice(null);
  }, []);

  /**
   * Refresh device info from device
   */
  const handleRefreshInfo = useCallback(async (deviceId) => {
    try {
      await refreshDeviceInfo.mutateAsync(deviceId);
    } catch (error) {
      // Error is handled by the mutation's onError
      console.error('Refresh info error:', error);
    }
  }, [refreshDeviceInfo]);

  // ==================== Render ====================

  // Loading state
  if (isLoading) {
    return (
      <Box>
        <PageHeader
          title="Devices"
          subtitle="Manage your PV modules and inverters"
        />
        <LoadingSpinner message="Loading devices..." />
      </Box>
    );
  }

  // Error state
  if (isError) {
    return (
      <Box>
        <PageHeader
          title="Devices"
          subtitle="Manage your PV modules and inverters"
        />
        <ErrorDisplay
          title="Failed to load devices"
          message={error?.message || 'An unknown error occurred'}
          onRetry={refetch}
        />
      </Box>
    );
  }

  // Empty state
  if (devices.length === 0) {
    return (
      <Box>
        <PageHeader
          title="Devices"
          subtitle="Manage your PV modules and inverters"
          actions={
            <Button
              variant="contained"
              color="primary"
              startIcon={<AddIcon />}
              onClick={handleAddDevice}
            >
              Add Device
            </Button>
          }
        />

        <EmptyState
          title="No devices configured yet"
          description="Add your first PV device to start monitoring."
          icon={<RouterIcon sx={{ fontSize: 48, opacity: 0.5 }} />}
          actionLabel="Add Your First Device"
          onAction={handleAddDevice}
        />

        {/* Form Dialog */}
        <DeviceForm
          open={formDialogOpen}
          onClose={handleFormClose}
          onSubmit={handleFormSubmit}
          device={selectedDevice}
          isSubmitting={isCreating || isUpdating}
          onTestConnection={testConnection}
        />
      </Box>
    );
  }

  // Normal state with devices
  return (
    <Box>
      <PageHeader
        title="Devices"
        subtitle={`${devices.length} device${devices.length === 1 ? '' : 's'} configured`}
        actions={
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={handleAddDevice}
          >
            Add Device
          </Button>
        }
      />

      {/* Device List */}
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

      {/* Form Dialog (Add/Edit) */}
      <DeviceForm
        open={formDialogOpen}
        onClose={handleFormClose}
        onSubmit={handleFormSubmit}
        device={selectedDevice}
        isSubmitting={isCreating || isUpdating}
        onTestConnection={testConnection}
      />

      {/* Delete Confirmation Dialog */}
      <DeviceDeleteDialog
        open={deleteDialogOpen}
        onClose={handleDeleteClose}
        onConfirm={handleDeleteConfirm}
        device={selectedDevice}
        isDeleting={isDeleting}
      />

      {/* Device Info Dialog */}
      <DeviceInfoDialog
        open={infoDialogOpen}
        onClose={handleInfoClose}
        onRefresh={handleRefreshInfo}
        device={selectedDevice}
        deviceInfo={deviceInfo?.identification ? { ...deviceInfo.identification, lastUpdated: deviceInfo.lastUpdated } : null}
        isLoading={isInfoLoading}
        isRefreshing={refreshDeviceInfo.isPending}
      />
    </Box>
  );
}

export default DevicesPage;
