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
