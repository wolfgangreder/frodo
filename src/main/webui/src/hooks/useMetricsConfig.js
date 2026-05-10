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

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metricsApi } from '../services';
import { useUiStore } from '../stores';

/**
 * Query key factory for metrics
 */
export const metricsKeys = {
  all: ['metrics'],
  config: (deviceId) => [...metricsKeys.all, 'config', deviceId],
  availableParameters: (deviceId) => [...metricsKeys.all, 'available-parameters', deviceId],
  status: (deviceId) => [...metricsKeys.all, 'status', deviceId],
  data: (deviceId, params) => [...metricsKeys.all, 'data', deviceId, params],
  latestData: (deviceId) => [...metricsKeys.all, 'latest', deviceId],
};

/**
 * Hook to fetch metrics configuration for a device
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 * @returns {Object} Query result with metrics config data
 */
export function useMetricsConfig(deviceId, options = {}) {
  return useQuery({
    queryKey: metricsKeys.config(deviceId),
    queryFn: () => metricsApi.getConfig(deviceId),
    enabled: !!deviceId,
    staleTime: 30 * 1000, // 30 seconds
    ...options,
  });
}

/**
 * Hook to fetch available SunSpec parameters for a device
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 * @returns {Object} Query result with available parameters
 */
export function useAvailableParameters(deviceId, options = {}) {
  return useQuery({
    queryKey: metricsKeys.availableParameters(deviceId),
    queryFn: () => metricsApi.getAvailableParameters(deviceId),
    enabled: !!deviceId,
    staleTime: 5 * 60 * 1000, // 5 minutes (model discovery is expensive)
    ...options,
  });
}

/**
 * Hook to fetch current scraping status for a device
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 * @returns {Object} Query result with scraping status
 */
export function useMetricsStatus(deviceId, options = {}) {
  return useQuery({
    queryKey: metricsKeys.status(deviceId),
    queryFn: () => metricsApi.getStatus(deviceId),
    enabled: !!deviceId,
    refetchInterval: 15 * 1000, // Auto-refresh every 15 seconds
    ...options,
  });
}

/**
 * Hook to fetch latest metrics data for a device
 * @param {number} deviceId - Device ID
 * @param {Object} options - React Query options
 * @returns {Object} Query result with latest data
 */
export function useLatestMetrics(deviceId, options = {}) {
  return useQuery({
    queryKey: metricsKeys.latestData(deviceId),
    queryFn: () => metricsApi.getLatestData(deviceId),
    enabled: !!deviceId,
    refetchInterval: 30 * 1000, // Auto-refresh every 30 seconds
    ...options,
  });
}

/**
 * Hook to update metrics configuration
 * @returns {Object} Mutation result
 */
export function useUpdateMetricsConfig() {
  const queryClient = useQueryClient();
  const showNotification = useUiStore((state) => state.showNotification);

  return useMutation({
    mutationFn: ({ deviceId, config }) => metricsApi.updateConfig(deviceId, config),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: metricsKeys.config(variables.deviceId) });
      queryClient.invalidateQueries({ queryKey: metricsKeys.status(variables.deviceId) });
      showNotification('Metrics configuration saved', 'success');
    },
    onError: (error) => {
      const message = error.response?.data?.message || error.message || 'Failed to save metrics configuration';
      showNotification(message, 'error');
    },
  });
}
