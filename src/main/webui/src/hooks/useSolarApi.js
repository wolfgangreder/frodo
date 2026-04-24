import { useQuery } from '@tanstack/react-query';
import { solarApiService } from '../services';

/**
 * Query key factory for Solar API
 */
export const solarApiKeys = {
  all: ['solar-api'],
  status: () => [...solarApiKeys.all, 'status'],
};

/**
 * Hook to fetch Solar API status and live power flow values.
 * Auto-refreshes every 5 seconds when enabled.
 *
 * @param {Object} options - React Query options
 * @returns {Object} Query result with Solar API status data
 */
export function useSolarApiStatus(options = {}) {
  return useQuery({
    queryKey: solarApiKeys.status(),
    queryFn: () => solarApiService.getStatus(),
    refetchInterval: 5 * 1000,
    staleTime: 4 * 1000,
    ...options,
  });
}
