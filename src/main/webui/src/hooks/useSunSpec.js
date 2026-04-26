import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { sunspecApi } from '../services';
import useUiStore from '../stores/useUiStore';

/**
 * Query key factory for SunSpec data
 */
export const sunspecKeys = {
  all: ['sunspec'],
  discovery: (deviceId) => [...sunspecKeys.all, 'discovery', deviceId],
  common: (deviceId) => [...sunspecKeys.all, 'common', deviceId],
  inverter: (deviceId) => [...sunspecKeys.all, 'inverter', deviceId],
  controls: (deviceId) => [...sunspecKeys.all, 'controls', deviceId],
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

/**
 * Hook to fetch Immediate Controls model (Model 123).
 * Contains power limit state: WMaxLimPct, WMaxLim_Ena, PF control settings.
 * @param {number}  deviceId - Device ID
 * @param {boolean} enabled  - Whether to run the query (default true)
 * @param {Object}  options  - React Query options
 */
export function useSunSpecControls(deviceId, enabled = true, options = {}) {
  return useQuery({
    queryKey: sunspecKeys.controls(deviceId),
    queryFn: () => sunspecApi.getControls(deviceId),
    enabled: !!deviceId && enabled,
    staleTime: 10 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: 30 * 1000, // 30 s – control state changes rarely on its own
    ...options,
  });
}

/**
 * Mutation hook to set the inverter power output limit via Model 123.
 *
 * On success, invalidates the controls query so callers see the latest state.
 * On error, shows an error notification via the global UI store.
 *
 * @param {number} deviceId - Device ID
 * @returns {import('@tanstack/react-query').UseMutationResult}
 *   Call mutate({ enable, limitWatts?, rampSeconds?, revertSeconds? }) to trigger.
 *   When enable=true and limitWatts is provided (≥ 1) a fixed watt cap is applied;
 *   otherwise the server uses the Smart Meter for closed-loop zero-export control.
 */
export function useSetPowerLimit(deviceId) {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ enable, limitWatts, rampSeconds = 0, revertSeconds = 0 }) =>
      sunspecApi.setPowerLimit(deviceId, enable, limitWatts, rampSeconds, revertSeconds),

    onSuccess: (_data, variables) => {
      // Refresh controls so the displayed WMaxLim_Ena state is up-to-date
      queryClient.invalidateQueries({ queryKey: sunspecKeys.controls(deviceId) });
      let msg;
      if (variables.enable) {
        msg = variables.limitWatts != null
          ? `Grid export capped at ${variables.limitWatts} W`
          : 'Grid export blocked (zero-export active)';
      } else {
        msg = 'Grid export re-enabled';
      }
      showSuccess(msg);
    },

    onError: (error) => {
      const status = error?.response?.status;
      const serverMsg = error?.response?.data?.message;
      if (status === 409) {
        showError('Write operations are disabled. Set frodo.modbus.write-enabled=true to allow control commands.');
      } else if (status === 404) {
        // Server provides a specific message (e.g. "No Smart Meter found", "Model 123 not found")
        showError(serverMsg ?? 'Device or required model not found.');
      } else {
        showError(`Failed to set power limit: ${serverMsg ?? error.message}`);
      }
    },
  });
}

// ========== Export Schedule ==========

export const scheduleKeys = {
  all: ['export-schedule'],
  forDevice: (deviceId) => [...scheduleKeys.all, deviceId],
};

/**
 * Fetches the daily recurring grid-export schedule for a device.
 * Returns null (no error) when no schedule is configured (HTTP 404).
 *
 * @param {number}  deviceId - Device ID
 * @param {boolean} enabled  - Whether to run the query (default true)
 */
export function useExportSchedule(deviceId, enabled = true) {
  return useQuery({
    queryKey: scheduleKeys.forDevice(deviceId),
    queryFn: () => sunspecApi.getExportSchedule(deviceId),
    enabled: !!deviceId && enabled,
    staleTime: 30 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

/**
 * Mutation to create or replace the grid-export schedule for a device.
 * Invalidates the schedule query on success.
 *
 * @param {number} deviceId - Device ID
 * @returns {import('@tanstack/react-query').UseMutationResult}
 *   Call mutate({ enabled, blockFrom, enableFrom, strategy?, limitWatts? }) to trigger.
 */
export function useSetExportSchedule(deviceId) {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ enabled, blockFrom, enableFrom, strategy, limitWatts }) =>
      sunspecApi.setExportSchedule(deviceId, enabled, blockFrom, enableFrom, strategy, limitWatts),

    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: scheduleKeys.forDevice(deviceId) });
      showSuccess(
        variables.enabled
          ? `Schedule saved: block ${variables.blockFrom} – ${variables.enableFrom}`
          : 'Schedule saved (disabled)'
      );
    },

    onError: (error) => {
      showError(
        `Failed to save schedule: ${error?.response?.data?.message ?? error.message}`
      );
    },
  });
}

/**
 * Mutation to delete the grid-export schedule for a device.
 * Invalidates the schedule query on success.
 *
 * @param {number} deviceId - Device ID
 */
export function useDeleteExportSchedule(deviceId) {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: () => sunspecApi.deleteExportSchedule(deviceId),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: scheduleKeys.forDevice(deviceId) });
      showSuccess('Export schedule deleted');
    },

    onError: (error) => {
      showError(
        `Failed to delete schedule: ${error?.response?.data?.message ?? error.message}`
      );
    },
  });
}
