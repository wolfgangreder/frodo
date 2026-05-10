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

import { create } from 'zustand';

/**
 * Device Store - manages device-related state
 * - Selected device for detail view
 * - Device list cache (supplementary to React Query)
 * - Device form state for create/edit dialogs
 */
const useDeviceStore = create((set, get) => ({
  // Selected device ID (for detail views)
  selectedDeviceId: null,

  // Device being edited (form state)
  editingDevice: null,

  // Dialog states
  isCreateDialogOpen: false,
  isEditDialogOpen: false,
  isDeleteDialogOpen: false,
  deviceToDelete: null,

  // Connection test state
  connectionTestResult: null, // { success, message, deviceInfo }
  isTestingConnection: false,

  // Actions
  selectDevice: (deviceId) => set({ selectedDeviceId: deviceId }),
  clearSelectedDevice: () => set({ selectedDeviceId: null }),

  // Create dialog actions
  openCreateDialog: () => set({
    isCreateDialogOpen: true,
    editingDevice: {
      name: '',
      host: '',
      port: 502,
      unitId: 1,
      enabled: true,
    },
    connectionTestResult: null,
  }),
  closeCreateDialog: () => set({
    isCreateDialogOpen: false,
    editingDevice: null,
    connectionTestResult: null,
  }),

  // Edit dialog actions
  openEditDialog: (device) => set({
    isEditDialogOpen: true,
    editingDevice: { ...device },
    connectionTestResult: null,
  }),
  closeEditDialog: () => set({
    isEditDialogOpen: false,
    editingDevice: null,
    connectionTestResult: null,
  }),

  // Delete dialog actions
  openDeleteDialog: (device) => set({
    isDeleteDialogOpen: true,
    deviceToDelete: device,
  }),
  closeDeleteDialog: () => set({
    isDeleteDialogOpen: false,
    deviceToDelete: null,
  }),

  // Form field updates
  updateEditingDevice: (field, value) => set((state) => ({
    editingDevice: state.editingDevice
      ? { ...state.editingDevice, [field]: value }
      : null,
  })),

  // Batch update for multiple fields
  updateEditingDeviceFields: (fields) => set((state) => ({
    editingDevice: state.editingDevice
      ? { ...state.editingDevice, ...fields }
      : null,
  })),

  // Connection test actions
  setConnectionTestResult: (result) => set({ connectionTestResult: result }),
  clearConnectionTestResult: () => set({ connectionTestResult: null }),
  setTestingConnection: (testing) => set({ isTestingConnection: testing }),
}));

export default useDeviceStore;
