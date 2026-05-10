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

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import {
  ParameterSelector,
  ScrapingIntervalInput,
  MetricsStatusCard,
} from '../components/metrics';
import {
  useMetricsConfig,
  useAvailableParameters,
  useMetricsStatus,
  useUpdateMetricsConfig,
  useDevice,
} from '../hooks';

/**
 * MetricsConfigPage - configure per-device metrics scraping
 */
function MetricsConfigPage() {
  const { id: deviceId } = useParams();
  const navigate = useNavigate();
  const parsedDeviceId = deviceId ? Number(deviceId) : null;

  // Queries
  const { data: device, isLoading: isDeviceLoading } = useDevice(parsedDeviceId);
  const { data: config, isLoading: isConfigLoading } = useMetricsConfig(parsedDeviceId);
  const {
    data: availableParams,
    isLoading: isParamsLoading,
    isError: isParamsError,
    error: paramsError,
  } = useAvailableParameters(parsedDeviceId);
  const { data: status, isLoading: isStatusLoading } = useMetricsStatus(parsedDeviceId);

  // Mutation
  const updateConfig = useUpdateMetricsConfig();

  // Form state
  const [enabled, setEnabled] = useState(false);
  const [scrapeIntervalSeconds, setScrapeIntervalSeconds] = useState(30);
  const [storeToDatabase, setStoreToDatabase] = useState(true);
  const [retentionDays, setRetentionDays] = useState(365);
  const [selectedParameters, setSelectedParameters] = useState([]);
  const [parameterModes, setParameterModes] = useState({});
  const [isDirty, setIsDirty] = useState(false);

  // Initialize form from config
  useEffect(() => {
    if (config) {
      setEnabled(config.enabled);
      setScrapeIntervalSeconds(config.scrapeIntervalSeconds);
      setStoreToDatabase(config.storeToDatabase ?? true);
      setRetentionDays(config.retentionDays ?? 365);

      // Build selected parameter keys and mode map from config
      const enabledParams = (config.parameters || []).filter((p) => p.enabled);
      const keys = enabledParams.map((p) => `${p.sunspecModelId}_${p.fieldName}`);
      setSelectedParameters(keys);

      const modes = {};
      enabledParams.forEach((p) => {
        modes[`${p.sunspecModelId}_${p.fieldName}`] = p.aggregationMode || 'MINUTE_AVERAGE';
      });
      setParameterModes(modes);
      setIsDirty(false);
    }
  }, [config]);

  // Track changes
  const handleEnabledChange = useCallback((_, checked) => {
    setEnabled(checked);
    setIsDirty(true);
  }, []);

  const handleIntervalChange = useCallback((value) => {
    setScrapeIntervalSeconds(value);
    setIsDirty(true);
  }, []);

  const handleStoreToDatabaseChange = useCallback((_, checked) => {
    setStoreToDatabase(checked);
    setIsDirty(true);
  }, []);

  const handleRetentionChange = useCallback((e) => {
    setRetentionDays(Number(e.target.value));
    setIsDirty(true);
  }, []);

  const handleParameterSelectionChange = useCallback((newSelection) => {
    setSelectedParameters(newSelection);
    setIsDirty(true);
  }, []);

  const handleModeChange = useCallback((key, mode) => {
    setParameterModes((prev) => ({ ...prev, [key]: mode }));
    setIsDirty(true);
  }, []);

  // Build parameters array for API request
  const buildParametersPayload = useCallback(() => {
    return selectedParameters.map((key) => {
      const [modelId, ...fieldParts] = key.split('_');
      const fieldName = fieldParts.join('_');
      return {
        sunspecModelId: parseInt(modelId, 10),
        fieldName,
        enabled: true,
        customMetricName: null,
        aggregationMode: parameterModes[key] || 'MINUTE_AVERAGE',
      };
    });
  }, [selectedParameters, parameterModes]);

  // Save handler
  const handleSave = useCallback(async () => {
    try {
      await updateConfig.mutateAsync({
        deviceId: parsedDeviceId,
        config: {
          scrapeIntervalSeconds,
          enabled,
          storeToDatabase,
          retentionDays,
          parameters: buildParametersPayload(),
        },
      });
      setIsDirty(false);
    } catch (error) {
      // Error handled by mutation onError
      console.error('Save failed:', error);
    }
  }, [parsedDeviceId, scrapeIntervalSeconds, enabled, storeToDatabase, retentionDays, buildParametersPayload, updateConfig]);

  // Available parameters for the selector
  const availableParametersList = useMemo(() => {
    return availableParams?.parameters || [];
  }, [availableParams]);

  const isDiscoveryBased = availableParams?.discoveryBased ?? true;

  // Loading state
  const isLoading = isDeviceLoading || isConfigLoading;

  if (isLoading) {
    return (
      <Box>
        <PageHeader
          title="Metrics Configuration"
          subtitle="Loading..."
        />
        <LoadingSpinner message="Loading metrics configuration..." />
      </Box>
    );
  }

  if (!device) {
    return (
      <Box>
        <PageHeader title="Metrics Configuration" />
        <ErrorDisplay
          title="Device not found"
          message={`Device with ID ${deviceId} was not found.`}
          onRetry={() => navigate('/devices')}
        />
      </Box>
    );
  }

  return (
    <Box>
      <PageHeader
        title="Metrics Configuration"
        subtitle={`${device.name} (${device.host}:${device.port} unit ${device.unitId})`}
        actions={
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              startIcon={<ArrowBackIcon />}
              onClick={() => navigate('/devices')}
            >
              Back
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<SaveIcon />}
              onClick={handleSave}
              disabled={!isDirty || updateConfig.isPending}
            >
              {updateConfig.isPending ? 'Saving...' : 'Save'}
            </Button>
          </Stack>
        }
      />

      {/* Status Card */}
      <MetricsStatusCard status={status} isLoading={isStatusLoading} />

      {/* Configuration Form */}
      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Scraping Settings
          </Typography>

          {/* Enable/Disable Toggle */}
          <FormControlLabel
            control={
              <Switch
                checked={enabled}
                onChange={handleEnabledChange}
                color="primary"
              />
            }
            label={enabled ? 'Scraping enabled' : 'Scraping disabled'}
            sx={{ mb: 2, display: 'block' }}
          />

          {/* Scrape Interval */}
          <Box sx={{ mb: 3, opacity: enabled ? 1 : 0.5 }}>
            <ScrapingIntervalInput
              value={scrapeIntervalSeconds}
              onChange={handleIntervalChange}
              disabled={!enabled}
            />
          </Box>

          <Divider sx={{ my: 2 }} />

          {/* Database Storage Settings */}
          <Typography variant="h6" gutterBottom>
            Data Storage
          </Typography>

          <FormControlLabel
            control={
              <Switch
                checked={storeToDatabase}
                onChange={handleStoreToDatabaseChange}
                color="primary"
                disabled={!enabled}
              />
            }
            label="Store metrics to database (for historical queries)"
            sx={{ mb: 2, display: 'block', opacity: enabled ? 1 : 0.5 }}
          />

          {storeToDatabase && enabled && (
            <TextField
              select
              label="Data Retention"
              value={retentionDays}
              onChange={handleRetentionChange}
              size="small"
              sx={{ minWidth: 200, mb: 2 }}
            >
              <MenuItem value={30}>30 days</MenuItem>
              <MenuItem value={90}>90 days</MenuItem>
              <MenuItem value={180}>180 days</MenuItem>
              <MenuItem value={365}>1 year</MenuItem>
              <MenuItem value={730}>2 years</MenuItem>
            </TextField>
          )}

          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
            Metrics are always exposed to Prometheus regardless of database storage setting.
          </Typography>
        </CardContent>
      </Card>

      {/* Parameter Selection */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Parameters to Collect
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Select which SunSpec fields to scrape from this device. Only numeric fields are available.
          </Typography>

          {isParamsLoading ? (
            <LoadingSpinner message="Discovering SunSpec parameters..." />
          ) : isParamsError ? (
            <ErrorDisplay
              title="Failed to discover parameters"
              message={paramsError?.message || 'Could not read SunSpec models from device. Is the device online?'}
            />
          ) : (
            <ParameterSelector
              availableParameters={availableParametersList}
              selectedParameters={selectedParameters}
              onSelectionChange={handleParameterSelectionChange}
              disabled={false}
              discoveryBased={isDiscoveryBased}
              showModeSelector={storeToDatabase && enabled}
              parameterModes={parameterModes}
              onModeChange={handleModeChange}
            />
          )}
        </CardContent>
      </Card>
    </Box>
  );
}

export default MetricsConfigPage;
