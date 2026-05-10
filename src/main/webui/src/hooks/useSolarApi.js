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
