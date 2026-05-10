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

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { gpioApi } from '../services';
import useUiStore from '../stores/useUiStore';

/**
 * Query key factory for GPIO resources.
 */
export const gpioKeys = {
  all: ['gpio'],
  status: () => [...gpioKeys.all, 'status'],
  assignments: () => [...gpioKeys.all, 'assignments'],
  assignment: (deviceId) => [...gpioKeys.all, 'assignments', deviceId],
};

/**
 * Hook to fetch GPIO system + per-pair status.
 * Auto-refreshes every 3 seconds for live pin state.
 */
export function useGpioStatus(options = {}) {
  return useQuery({
    queryKey: gpioKeys.status(),
    queryFn: () => gpioApi.getStatus(),
    refetchInterval: 3000,
    staleTime: 2000,
    ...options,
  });
}

/**
 * Hook to fetch all GPIO assignments.
 */
export function useGpioAssignments(options = {}) {
  return useQuery({
    queryKey: gpioKeys.assignments(),
    queryFn: () => gpioApi.getAssignments(),
    staleTime: 30_000,
    ...options,
  });
}

/**
 * Mutation to set manual output override for a GPIO pair.
 */
export function useSetManualOutput() {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);

  return useMutation({
    mutationFn: ({ name, high }) => gpioApi.setManualOutput(name, high),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gpioKeys.status() });
    },
    onError: (error) => {
      showError(`Failed to set manual output: ${error?.data?.message ?? error.message}`);
    },
  });
}

/**
 * Mutation to clear manual output override for a GPIO pair.
 */
export function useClearManualOutput() {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);

  return useMutation({
    mutationFn: ({ name }) => gpioApi.clearManualOutput(name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gpioKeys.status() });
    },
    onError: (error) => {
      showError(`Failed to clear manual output: ${error?.data?.message ?? error.message}`);
    },
  });
}

/**
 * Mutation to create or update a GPIO pair assignment.
 */
export function useSetGpioAssignment() {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ deviceId, gpioPairName }) => gpioApi.setAssignment(deviceId, gpioPairName),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gpioKeys.assignments() });
      queryClient.invalidateQueries({ queryKey: gpioKeys.status() });
      showSuccess('GPIO assignment saved');
    },
    onError: (error) => {
      showError(`Failed to save GPIO assignment: ${error?.data?.message ?? error.message}`);
    },
  });
}

/**
 * Mutation to delete a GPIO pair assignment.
 */
export function useDeleteGpioAssignment() {
  const queryClient = useQueryClient();
  const showError = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ deviceId }) => gpioApi.deleteAssignment(deviceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gpioKeys.assignments() });
      queryClient.invalidateQueries({ queryKey: gpioKeys.status() });
      showSuccess('GPIO assignment removed');
    },
    onError: (error) => {
      showError(`Failed to remove GPIO assignment: ${error?.data?.message ?? error.message}`);
    },
  });
}
