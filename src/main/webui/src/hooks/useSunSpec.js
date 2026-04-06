import { useQuery } from '@tanstack/react-query';
import { sunspecApi } from '../services';

/**
 * Query key factory for SunSpec data
 */
export const sunspecKeys = {
  all: ['sunspec'],
  discovery: (deviceId) => [...sunspecKeys.all, 'discovery', deviceId],
  common: (deviceId) => [...sunspecKeys.all, 'common', deviceId],
  inverter: (deviceId) => [...sunspecKeys.all, 'inverter', deviceId],
  storage: (deviceId) => [...sunspecKeys.all, 'storage', deviceId],
  status: (deviceId) => [...sunspecKeys.all, 'status', deviceId],
  nameplate: (deviceId) => [...sunspecKeys.all, 'nameplate', deviceId],
  mppt: (deviceId) => [...sunspecKeys.all, 'mppt', deviceId],
  model: (deviceId, modelId) => [...sunspecKeys.all, 'model', deviceId, modelId],
  models: (deviceId) => [...sunspecKeys.all, 'models', deviceId],
};

/**
 * Hook to discover SunSpec models on a device.
 * When the device is unreachable, retries every 60 s to detect reconnection.
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecDiscovery(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.discovery(deviceId),
    queryFn: () => sunspecApi.discover(deviceId),
    enabled: !!deviceId,
    staleTime: 5 * 60 * 1000, // 5 min - discovery rarely changes
    retry: false,
    // Poll every 60 s while offline so the dashboard reconnects automatically
    refetchInterval: (query) =>
      query.state.status === 'error' ? 60 * 1000 : false,
    ...options,
  });
}

/**
 * Hook to fetch SunSpec Common model data (Model 1)
 * Device identification: manufacturer, model, serial, firmware
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecCommon(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.common(deviceId),
    queryFn: () => sunspecApi.getCommon(deviceId),
    enabled: !!deviceId,
    staleTime: 5 * 60 * 1000, // 5 min - static data
    retry: false,
    refetchOnWindowFocus: false,
    ...options,
  });
}

/**
 * Hook to fetch inverter data (auto-detected model 101-103 or 111-113)
 * Real-time AC/DC power, voltage, current, frequency, energy, status
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecInverter(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.inverter(deviceId),
    queryFn: () => sunspecApi.getInverter(deviceId),
    enabled: !!deviceId,
    staleTime: 5 * 1000, // 5s - real-time data
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: 10 * 1000, // auto-refresh every 10 s when enabled
    ...options,
  });
}

/**
 * Hook to fetch storage/battery data (Model 124)
 * Charge state, battery voltage/current, charge status
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecStorage(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.storage(deviceId),
    queryFn: () => sunspecApi.getStorage(deviceId),
    enabled: !!deviceId,
    staleTime: 10 * 1000, // 10s
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: 15 * 1000, // auto-refresh every 15 s when enabled
    ...options,
  });
}

/**
 * Hook to fetch extended measurements & status (Model 122)
 * Lifetime energy, operating state, connection status
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecStatus(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.status(deviceId),
    queryFn: () => sunspecApi.getStatus(deviceId),
    enabled: !!deviceId,
    staleTime: 10 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: 15 * 1000,
    ...options,
  });
}

/**
 * Hook to fetch nameplate ratings (Model 120)
 * Rated power, voltage, current - static device specs
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecNameplate(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.nameplate(deviceId),
    queryFn: () => sunspecApi.getNameplate(deviceId),
    enabled: !!deviceId,
    staleTime: 30 * 60 * 1000, // 30 min - rarely changes
    retry: false,
    refetchOnWindowFocus: false,
    ...options,
  });
}

/**
 * Hook to fetch MPPT extension data (Model 160)
 * Per-string DC inputs, module-level data
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 */
export function useSunSpecMppt(deviceId, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.mppt(deviceId),
    queryFn: () => sunspecApi.getMppt(deviceId),
    enabled: !!deviceId,
    staleTime: 10 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: 15 * 1000,
    ...options,
  });
}
