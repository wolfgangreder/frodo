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
  Button,
  Card,
  CardBody,
  Divider,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Switch,
  Title,
} from '@patternfly/react-core';
import { ArrowLeftIcon, SaveIcon } from '@patternfly/react-icons';
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

const C = {
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
};

/**
 * MetricsConfigPage - configure per-device metrics scraping
 */
function MetricsConfigPage() {
  const { id: deviceId } = useParams();
  const navigate = useNavigate();
  const parsedDeviceId = deviceId ? Number(deviceId) : null;

  const { data: device, isLoading: isDeviceLoading } = useDevice(parsedDeviceId);
  const { data: config, isLoading: isConfigLoading } = useMetricsConfig(parsedDeviceId);
  const {
    data: availableParams,
    isLoading: isParamsLoading,
    isError: isParamsError,
    error: paramsError,
  } = useAvailableParameters(parsedDeviceId);
  const { data: status, isLoading: isStatusLoading } = useMetricsStatus(parsedDeviceId);

  const updateConfig = useUpdateMetricsConfig();

  const [enabled, setEnabled] = useState(false);
  const [scrapeIntervalSeconds, setScrapeIntervalSeconds] = useState(30);
  const [storeToDatabase, setStoreToDatabase] = useState(true);
  const [retentionDays, setRetentionDays] = useState(365);
  const [selectedParameters, setSelectedParameters] = useState([]);
  const [parameterModes, setParameterModes] = useState({});
  const [isDirty, setIsDirty] = useState(false);

  useEffect(() => {
    if (config) {
      setEnabled(config.enabled);
      setScrapeIntervalSeconds(config.scrapeIntervalSeconds);
      setStoreToDatabase(config.storeToDatabase ?? true);
      setRetentionDays(config.retentionDays ?? 365);

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

  const handleEnabledChange = useCallback((_event, checked) => {
    setEnabled(checked);
    setIsDirty(true);
  }, []);

  const handleIntervalChange = useCallback((value) => {
    setScrapeIntervalSeconds(value);
    setIsDirty(true);
  }, []);

  const handleStoreToDatabaseChange = useCallback((_event, checked) => {
    setStoreToDatabase(checked);
    setIsDirty(true);
  }, []);

  const handleRetentionChange = useCallback((_event, value) => {
    setRetentionDays(Number(value));
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
      console.error('Save failed:', error);
    }
  }, [parsedDeviceId, scrapeIntervalSeconds, enabled, storeToDatabase, retentionDays, buildParametersPayload, updateConfig]);

  const availableParametersList = useMemo(() => {
    return availableParams?.parameters || [];
  }, [availableParams]);

  const isDiscoveryBased = availableParams?.discoveryBased ?? true;
  const isLoading = isDeviceLoading || isConfigLoading;

  if (isLoading) {
    return (
      <div>
        <PageHeader title="Metrics Configuration" subtitle="Loading..." />
        <LoadingSpinner message="Loading metrics configuration..." />
      </div>
    );
  }

  if (!device) {
    return (
      <div>
        <PageHeader title="Metrics Configuration" />
        <ErrorDisplay
          title="Device not found"
          message={`Device with ID ${deviceId} was not found.`}
          onRetry={() => navigate('/devices')}
        />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Metrics Configuration"
        subtitle={`${device.name} (${device.host}:${device.port} unit ${device.unitId})`}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Button
              variant="secondary"
              icon={<ArrowLeftIcon />}
              onClick={() => navigate('/devices')}
            >
              Back
            </Button>
            <Button
              variant="primary"
              icon={<SaveIcon />}
              onClick={handleSave}
              isDisabled={!isDirty || updateConfig.isPending}
            >
              {updateConfig.isPending ? 'Saving...' : 'Save'}
            </Button>
          </div>
        }
      />

      <MetricsStatusCard status={status} isLoading={isStatusLoading} />

      <Card style={{ marginBottom: 16 }}>
        <CardBody>
          <Title headingLevel="h3" size="lg" style={{ marginBottom: 16 }}>
            Scraping Settings
          </Title>

          <Switch
            id="metrics-enabled"
            label="Scraping enabled"
            labelOff="Scraping disabled"
            isChecked={enabled}
            onChange={handleEnabledChange}
            style={{ marginBottom: 16, display: 'block' }}
          />

          <div style={{ marginBottom: 24, opacity: enabled ? 1 : 0.5 }}>
            <ScrapingIntervalInput
              value={scrapeIntervalSeconds}
              onChange={handleIntervalChange}
              disabled={!enabled}
            />
          </div>

          <Divider style={{ marginTop: 16, marginBottom: 16 }} />

          <Title headingLevel="h3" size="lg" style={{ marginBottom: 16 }}>
            Data Storage
          </Title>

          <Switch
            id="store-to-database"
            label="Store metrics to database (for historical queries)"
            isChecked={storeToDatabase}
            onChange={handleStoreToDatabaseChange}
            isDisabled={!enabled}
            style={{ marginBottom: 16, display: 'block', opacity: enabled ? 1 : 0.5 }}
          />

          {storeToDatabase && enabled && (
            <FormGroup label="Data Retention" fieldId="retention-days" style={{ maxWidth: 240, marginBottom: 16 }}>
              <FormSelect
                id="retention-days"
                value={String(retentionDays)}
                onChange={handleRetentionChange}
                aria-label="Data retention period"
              >
                <FormSelectOption value="30" label="30 days" />
                <FormSelectOption value="90" label="90 days" />
                <FormSelectOption value="180" label="180 days" />
                <FormSelectOption value="365" label="1 year" />
                <FormSelectOption value="730" label="2 years" />
              </FormSelect>
            </FormGroup>
          )}

          <p style={{ color: C.subtle, fontSize: '0.75rem', marginBottom: 4 }}>
            Metrics are always exposed to Prometheus regardless of database storage setting.
          </p>
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <Title headingLevel="h3" size="lg" style={{ marginBottom: 8 }}>
            Parameters to Collect
          </Title>
          <p style={{ color: C.subtle, fontSize: '0.875rem', marginBottom: 16 }}>
            Select which SunSpec fields to scrape from this device. Only numeric fields are available.
          </p>

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
        </CardBody>
      </Card>
    </div>
  );
}

export default MetricsConfigPage;
