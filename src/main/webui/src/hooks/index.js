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

/**
 * Custom hooks barrel export
 */
export {
  useDevices,
  useDeviceList,
  useDevice,
  useDeviceInfo,
  useCreateDevice,
  useUpdateDevice,
  useDeleteDevice,
  useRefreshDeviceInfo,
  useTestConnection,
  deviceKeys,
} from './useDevices';

export {
  useMetricsConfig,
  useAvailableParameters,
  useMetricsStatus,
  useLatestMetrics,
  useUpdateMetricsConfig,
  metricsKeys,
} from './useMetricsConfig';

export {
  useMetricsDocs,
  metricsDocsKeys,
} from './useMetricsDocs';

export {
  usePriceControl,
  useSetPriceControl,
  priceControlKeys,
} from './usePriceControl';

export {
  useSunSpecDiscovery,
  useSunSpecCommon,
  useSunSpecInverter,
  useSunSpecStorage,
  useSunSpecStatus,
  useSunSpecNameplate,
  useSunSpecMppt,
  sunspecKeys,
} from './useSunSpec';

export {
  useSolarApiStatus,
  solarApiKeys,
} from './useSolarApi';

export {
  useGpioStatus,
  useGpioAssignments,
  useSetManualOutput,
  useClearManualOutput,
  useSetGpioAssignment,
  useDeleteGpioAssignment,
  gpioKeys,
} from './useGpio';

export {
  useCostControlConfig,
  useUpdateCostControlConfig,
  useCostControlProviders,
  useCostControlPrices,
  useRefreshCostControlPrices,
  useSetManualImportPrice,
  useSetManualExportPrice,
  useHourlyCosts,
  useLatestHourlyCost,
  useMonthlyCosts,
  useMonthlyCost,
  useTariffWindows,
  useCreateTariffWindow,
  useUpdateTariffWindow,
  useDeleteTariffWindow,
  useGridFees,
  useCreateGridFee,
  useUpdateGridFee,
  useDeleteGridFee,
  useFixedCosts,
  useCreateFixedCost,
  useDeleteFixedCost,
  costControlKeys,
} from './useCostControl';
