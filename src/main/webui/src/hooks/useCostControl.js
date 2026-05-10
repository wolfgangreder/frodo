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
import { costControlApi } from '../services';
import useUiStore from '../stores/useUiStore';

/**
 * Query key factory for cost control.
 */
export const costControlKeys = {
  all: ['cost-control'],
  config: () => [...costControlKeys.all, 'config'],
  providers: () => [...costControlKeys.all, 'providers'],
  prices: () => [...costControlKeys.all, 'prices'],
  hourly: () => [...costControlKeys.all, 'hourly'],
  hourlyLatest: () => [...costControlKeys.hourly(), 'latest'],
  monthly: () => [...costControlKeys.all, 'monthly'],
  monthlyDetail: (ym) => [...costControlKeys.monthly(), ym],
  tariffWindows: () => [...costControlKeys.all, 'tariff-windows'],
  gridFees: () => [...costControlKeys.all, 'grid-fees'],
  fixedCosts: () => [...costControlKeys.all, 'fixed-costs'],
};

// ---- Config ----------------------------------------------------------------

export function useCostControlConfig(options = {}) {
  return useQuery({
    queryKey: costControlKeys.config(),
    queryFn: () => costControlApi.getConfig(),
    staleTime: 30 * 1000,
    ...options,
  });
}

export function useUpdateCostControlConfig() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => costControlApi.updateConfig(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.config() });
      showSuccess('Cost control configuration updated');
    },
    onError: (error) => {
      showError(`Failed to update config: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

// ---- Providers -------------------------------------------------------------

export function useCostControlProviders(options = {}) {
  return useQuery({
    queryKey: costControlKeys.providers(),
    queryFn: () => costControlApi.listProviders(),
    staleTime: 5 * 60 * 1000,
    ...options,
  });
}

// ---- Energy prices ---------------------------------------------------------

export function useCostControlPrices(limit = 24, options = {}) {
  return useQuery({
    queryKey: [...costControlKeys.prices(), limit],
    queryFn: () => costControlApi.getRecentPrices(limit),
    refetchInterval: 60 * 1000,
    staleTime: 30 * 1000,
    ...options,
  });
}

export function useRefreshCostControlPrices() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (direction) => costControlApi.refreshPrices(direction),
    onSuccess: (_data, direction) => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.prices() });
      showSuccess(`Price refresh triggered for ${direction}`);
    },
    onError: (error) => {
      showError(`Price refresh failed: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useSetManualImportPrice() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => costControlApi.setImportPrice(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.prices() });
      showSuccess('Manual import price saved');
    },
    onError: (error) => {
      showError(`Failed to save import price: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useSetManualExportPrice() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => costControlApi.setExportPrice(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.prices() });
      showSuccess('Manual export price saved');
    },
    onError: (error) => {
      showError(`Failed to save export price: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

// ---- Hourly cost -----------------------------------------------------------

export function useHourlyCosts(from, to, options = {}) {
  return useQuery({
    queryKey: [...costControlKeys.hourly(), from, to],
    queryFn: () => costControlApi.getHourlyCosts(from, to),
    staleTime: 60 * 1000,
    ...options,
  });
}

export function useLatestHourlyCost(options = {}) {
  return useQuery({
    queryKey: costControlKeys.hourlyLatest(),
    queryFn: () => costControlApi.getLatestHourlyCost(),
    refetchInterval: 5 * 60 * 1000,
    staleTime: 2 * 60 * 1000,
    ...options,
  });
}

// ---- Monthly cost ----------------------------------------------------------

export function useMonthlyCosts(options = {}) {
  return useQuery({
    queryKey: costControlKeys.monthly(),
    queryFn: () => costControlApi.getMonthlyCosts(),
    refetchInterval: 5 * 60 * 1000,
    staleTime: 2 * 60 * 1000,
    ...options,
  });
}

export function useMonthlyCost(yearMonth, options = {}) {
  return useQuery({
    queryKey: costControlKeys.monthlyDetail(yearMonth),
    queryFn: () => costControlApi.getMonthlyCost(yearMonth),
    enabled: !!yearMonth,
    staleTime: 2 * 60 * 1000,
    ...options,
  });
}

// ---- Tariff windows --------------------------------------------------------

export function useTariffWindows(direction, options = {}) {
  return useQuery({
    queryKey: [...costControlKeys.tariffWindows(), direction],
    queryFn: () => costControlApi.listTariffWindows(direction),
    staleTime: 30 * 1000,
    ...options,
  });
}

export function useCreateTariffWindow() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => costControlApi.createTariffWindow(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.tariffWindows() });
      showSuccess('Tariff window created');
    },
    onError: (error) => {
      showError(`Failed to create tariff window: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useUpdateTariffWindow() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ id, payload }) => costControlApi.updateTariffWindow(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.tariffWindows() });
      showSuccess('Tariff window updated');
    },
    onError: (error) => {
      showError(`Failed to update tariff window: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useDeleteTariffWindow() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (id) => costControlApi.deleteTariffWindow(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.tariffWindows() });
      showSuccess('Tariff window deleted');
    },
    onError: (error) => {
      showError(`Failed to delete tariff window: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

// ---- Grid fees -------------------------------------------------------------

export function useGridFees(options = {}) {
  return useQuery({
    queryKey: costControlKeys.gridFees(),
    queryFn: () => costControlApi.listGridFees(),
    staleTime: 30 * 1000,
    ...options,
  });
}

export function useCreateGridFee() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => costControlApi.createGridFee(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.gridFees() });
      showSuccess('Grid fee created');
    },
    onError: (error) => {
      showError(`Failed to create grid fee: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useUpdateGridFee() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ id, payload }) => costControlApi.updateGridFee(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.gridFees() });
      showSuccess('Grid fee updated');
    },
    onError: (error) => {
      showError(`Failed to update grid fee: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useDeleteGridFee() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (id) => costControlApi.deleteGridFee(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.gridFees() });
      showSuccess('Grid fee deleted');
    },
    onError: (error) => {
      showError(`Failed to delete grid fee: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

// ---- Fixed costs -----------------------------------------------------------

export function useFixedCosts(options = {}) {
  return useQuery({
    queryKey: costControlKeys.fixedCosts(),
    queryFn: () => costControlApi.listFixedCosts(),
    staleTime: 30 * 1000,
    ...options,
  });
}

export function useCreateFixedCost() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => costControlApi.createFixedCost(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.fixedCosts() });
      queryClient.invalidateQueries({ queryKey: costControlKeys.monthly() });
      showSuccess('Fixed cost saved');
    },
    onError: (error) => {
      showError(`Failed to save fixed cost: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useDeleteFixedCost() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (id) => costControlApi.deleteFixedCost(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.fixedCosts() });
      queryClient.invalidateQueries({ queryKey: costControlKeys.monthly() });
      showSuccess('Fixed cost deleted');
    },
    onError: (error) => {
      showError(`Failed to delete fixed cost: ${error?.message ?? 'Unknown error'}`);
    },
  });
}

export function useUpdateFixedCost() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: ({ id, payload }) => costControlApi.updateFixedCost(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: costControlKeys.fixedCosts() });
      queryClient.invalidateQueries({ queryKey: costControlKeys.monthly() });
      showSuccess('Fixed cost updated');
    },
    onError: (error) => {
      showError(`Failed to update fixed cost: ${error?.message ?? 'Unknown error'}`);
    },
  });
}
