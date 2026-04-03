import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { deviceApi } from '../services';
import { useUiStore } from '../stores';

/**
 * Query key factory for devices
 */
export const deviceKeys = {
  all: ['devices'],
  lists: () => [...deviceKeys.all, 'list'],
  list: (filters) => [...deviceKeys.lists(), filters],
  details: () => [...deviceKeys.all, 'detail'],
  detail: (id) => [...deviceKeys.details(), id],
  info: (id) => [...deviceKeys.all, 'info', id],
};

/**
 * Hook to fetch all devices
 * @param {Object} options - React Query options
 * @returns {Object} Query result with devices data
 */
export function useDeviceList(options = {}) {
  return useQuery({
    queryKey: deviceKeys.lists(),
    queryFn: deviceApi.getAll,
    staleTime: 30 * 1000, // 30 seconds
    ...options,
  });
}

/**
 * Hook to fetch a single device by ID
 * @param {number} id - Device ID
 * @param {Object} options - React Query options
 * @returns {Object} Query result with device data
 */
export function useDevice(id, options = {}) {
  return useQuery({
    queryKey: deviceKeys.detail(id),
    queryFn: () => deviceApi.getById(id),
    enabled: !!id,
    ...options,
  });
}

/**
 * Hook to fetch device identification info
 * @param {number} id - Device ID
 * @param {Object} options - React Query options
 * @returns {Object} Query result with device info
 */
export function useDeviceInfo(id, options = {}) {
  return useQuery({
    queryKey: deviceKeys.info(id),
    queryFn: () => deviceApi.getInfo(id),
    enabled: !!id,
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
}

/**
 * Hook to create a new device
 * @returns {Object} Mutation result
 */
export function useCreateDevice() {
  const queryClient = useQueryClient();
  const showNotification = useUiStore((state) => state.showNotification);

  return useMutation({
    mutationFn: deviceApi.create,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: deviceKeys.lists() });
      showNotification(`Device "${data.name}" created successfully`, 'success');
    },
    onError: (error) => {
      const message = error.response?.data?.message || error.message || 'Failed to create device';
      showNotification(message, 'error');
    },
  });
}

/**
 * Hook to update an existing device
 * @returns {Object} Mutation result
 */
export function useUpdateDevice() {
  const queryClient = useQueryClient();
  const showNotification = useUiStore((state) => state.showNotification);

  return useMutation({
    mutationFn: ({ id, device }) => deviceApi.update(id, device),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: deviceKeys.lists() });
      queryClient.invalidateQueries({ queryKey: deviceKeys.detail(variables.id) });
      showNotification(`Device "${data.name}" updated successfully`, 'success');
    },
    onError: (error) => {
      const message = error.response?.data?.message || error.message || 'Failed to update device';
      showNotification(message, 'error');
    },
  });
}

/**
 * Hook to delete a device
 * @returns {Object} Mutation result
 */
export function useDeleteDevice() {
  const queryClient = useQueryClient();
  const showNotification = useUiStore((state) => state.showNotification);

  return useMutation({
    mutationFn: deviceApi.delete,
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: deviceKeys.lists() });
      queryClient.removeQueries({ queryKey: deviceKeys.detail(id) });
      showNotification('Device deleted successfully', 'success');
    },
    onError: (error) => {
      const message = error.response?.data?.message || error.message || 'Failed to delete device';
      showNotification(message, 'error');
    },
  });
}

/**
 * Hook to refresh device identification info
 * @returns {Object} Mutation result
 */
export function useRefreshDeviceInfo() {
  const queryClient = useQueryClient();
  const showNotification = useUiStore((state) => state.showNotification);

  return useMutation({
    mutationFn: deviceApi.refreshInfo,
    onSuccess: (data, id) => {
      queryClient.invalidateQueries({ queryKey: deviceKeys.info(id) });
      showNotification('Device info refreshed', 'success');
    },
    onError: (error) => {
      const message = error.response?.data?.message || error.message || 'Failed to refresh device info';
      showNotification(message, 'error');
    },
  });
}

/**
 * Hook to test connection to a device
 * @returns {Object} Mutation result
 */
export function useTestConnection() {
  return useMutation({
    mutationFn: deviceApi.testConnection,
  });
}

/**
 * Combined hook for device operations
 * @returns {Object} All device-related queries and mutations
 */
export function useDevices() {
  const listQuery = useDeviceList();
  const createMutation = useCreateDevice();
  const updateMutation = useUpdateDevice();
  const deleteMutation = useDeleteDevice();
  const testConnectionMutation = useTestConnection();

  return {
    // Data
    devices: listQuery.data || [],
    isLoading: listQuery.isLoading,
    isError: listQuery.isError,
    error: listQuery.error,
    refetch: listQuery.refetch,

    // Mutations
    createDevice: createMutation.mutateAsync,
    isCreating: createMutation.isPending,

    updateDevice: updateMutation.mutateAsync,
    isUpdating: updateMutation.isPending,

    deleteDevice: deleteMutation.mutateAsync,
    isDeleting: deleteMutation.isPending,

    testConnection: testConnectionMutation.mutateAsync,
    isTesting: testConnectionMutation.isPending,
  };
}
