import { useQuery } from '@tanstack/react-query';
import { metricsDocsApi } from '../services';

/**
 * Query key factory for metrics documentation
 */
export const metricsDocsKeys = {
  all: ['metrics-docs'],
  docs: () => [...metricsDocsKeys.all, 'docs'],
};

/**
 * Hook to fetch metrics documentation (semantic metric definitions)
 * @param {Object} options - React Query options
 * @returns {Object} Query result with metrics docs data
 */
export function useMetricsDocs(options = {}) {
  return useQuery({
    queryKey: metricsDocsKeys.docs(),
    queryFn: () => metricsDocsApi.getDocs(),
    staleTime: 10 * 60 * 1000, // 10 minutes (metadata rarely changes)
    ...options,
  });
}
