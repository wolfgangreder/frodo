import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { priceControlApi } from '../services';
import useUiStore from '../stores/useUiStore';

/**
 * Query key factory for global price-control setting.
 */
export const priceControlKeys = {
  all: ['price-control'],
  setting: () => [...priceControlKeys.all, 'setting'],
};

/**
 * Hook to fetch the current global price-control setting.
 * Auto-refreshes every 60 seconds (price changes hourly).
 *
 * @param {Object} options - Additional React Query options
 * @returns {import('@tanstack/react-query').UseQueryResult}
 */
export function usePriceControl(options = {}) {
  return useQuery({
    queryKey: priceControlKeys.setting(),
    queryFn: () => priceControlApi.getSetting(),
    refetchInterval: 60 * 1000,
    staleTime: 30 * 1000,
    ...options,
  });
}

/**
 * Mutation to create or replace the global price-control setting.
 * Invalidates the setting query on success.
 *
 * Call {@code mutate({ enabled, exportToleranceWatts })} to trigger.
 *
 * @returns {import('@tanstack/react-query').UseMutationResult}
 */
export function useSetPriceControl() {
  const queryClient = useQueryClient();
  const showError   = useUiStore((s) => s.showError);
  const showSuccess = useUiStore((s) => s.showSuccess);

  return useMutation({
    mutationFn: (payload) => priceControlApi.setSetting(payload),

    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: priceControlKeys.setting() });
      showSuccess(
        variables.enabled
          ? 'Price-controlled export limiting enabled'
          : 'Price-controlled export limiting disabled'
      );
    },

    onError: (error) => {
      showError(
        `Failed to save price control setting: ${error?.response?.data?.message ?? error.message}`
      );
    },
  });
}
