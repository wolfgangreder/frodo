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

import React, { useState, useEffect } from 'react';
import {
  Modal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  TextInput,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Switch,
  Alert,
  Spinner,
  Divider,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { CheckCircleIcon, ExclamationCircleIcon } from '@patternfly/react-icons';
import ConnectionTestButton from './ConnectionTestButton';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #0066cc)',
  success: 'var(--pf-t--global--color--status--success--default, #3e8635)',
  danger:  'var(--pf-t--global--color--status--danger--default, #c9190b)',
  subtle:  'var(--pf-t--global--text-color--subtle, #6a6e73)',
};

/**
 * Default form values for new device
 */
const defaultFormValues = {
  name: '',
  host: '',
  port: 502,
  unitId: 1,
  enabled: true,
};

/**
 * Device form dialog for creating/editing devices
 *
 * @param {Object} props
 * @param {boolean} props.open - Whether dialog is open
 * @param {Function} props.onClose - Callback when dialog closes
 * @param {Function} props.onSubmit - Callback when form is submitted
 * @param {Object} props.device - Device to edit (null for create mode)
 * @param {boolean} props.isSubmitting - Whether form is being submitted
 * @param {Function} props.onTestConnection - Callback to test connection
 */
function DeviceForm({
  open,
  onClose,
  onSubmit,
  device = null,
  isSubmitting = false,
  onTestConnection,
}) {
  const [formValues, setFormValues] = useState(defaultFormValues);
  const [errors, setErrors]         = useState({});
  const [testResult, setTestResult] = useState(null);
  const [isTesting, setIsTesting]   = useState(false);

  const isEditMode = !!device;

  // Reset form when dialog opens/closes or device changes
  useEffect(() => {
    if (open) {
      if (device) {
        setFormValues({
          name:    device.name    || '',
          host:    device.host    || '',
          port:    device.port    || 502,
          unitId:  device.unitId  || 1,
          enabled: device.enabled ?? true,
        });
      } else {
        setFormValues(defaultFormValues);
      }
      setErrors({});
      setTestResult(null);
    }
  }, [open, device]);

  // Clear test result when connection params change
  useEffect(() => {
    setTestResult(null);
  }, [formValues.host, formValues.port, formValues.unitId]);

  const setField = (name, value) => {
    setFormValues((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: null }));
  };

  const validate = () => {
    const newErrors = {};
    if (!formValues.name.trim()) newErrors.name = 'Name is required';
    if (!formValues.host.trim()) newErrors.host = 'Host is required';
    if (!formValues.port || formValues.port < 1 || formValues.port > 65535)
      newErrors.port = 'Port must be between 1 and 65535';
    if (formValues.unitId === '' || formValues.unitId < 1 || formValues.unitId > 247)
      newErrors.unitId = 'Unit ID must be between 1 and 247';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (validate()) {
      onSubmit({
        ...formValues,
        port:   parseInt(formValues.port,   10),
        unitId: parseInt(formValues.unitId, 10),
      });
    }
  };

  const handleTestConnection = async () => {
    const connectionErrors = {};
    if (!formValues.host.trim())
      connectionErrors.host = 'Host is required for testing';
    if (!formValues.port || formValues.port < 1 || formValues.port > 65535)
      connectionErrors.port = 'Valid port required for testing';
    if (formValues.unitId === '' || formValues.unitId < 1 || formValues.unitId > 247)
      connectionErrors.unitId = 'Valid Unit ID required for testing';

    if (Object.keys(connectionErrors).length > 0) {
      setErrors((prev) => ({ ...prev, ...connectionErrors }));
      return;
    }

    setIsTesting(true);
    setTestResult(null);

    try {
      const result = await onTestConnection({
        host:   formValues.host,
        port:   parseInt(formValues.port,   10),
        unitId: parseInt(formValues.unitId, 10),
      });
      setTestResult({ success: true, data: result });
    } catch (error) {
      setTestResult({
        success: false,
        error: error.response?.data?.message || error.message || 'Connection failed',
      });
    } finally {
      setIsTesting(false);
    }
  };

  const canTest =
    formValues.host.trim() &&
    formValues.port >= 1 && formValues.port <= 65535 &&
    formValues.unitId >= 1 && formValues.unitId <= 247;

  return (
    <Modal
      isOpen={open}
      onClose={onClose}
      variant="medium"
      aria-labelledby="device-form-title"
    >
      <ModalHeader title={isEditMode ? 'Edit Device' : 'Add New Device'} labelId="device-form-title" />
      <ModalBody>
        <form id="device-form" onSubmit={handleSubmit}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem', paddingTop: '0.5rem' }}>

            {/* Device Name */}
            <FormGroup label="Device Name" fieldId="device-name" isRequired>
              <TextInput
                id="device-name"
                value={formValues.name}
                onChange={(_event, value) => setField('name', value)}
                validated={errors.name ? 'error' : 'default'}
                required
                autoFocus
                aria-label="Device name"
              />
              <FormHelperText>
                <HelperText>
                  <HelperTextItem variant={errors.name ? 'error' : 'default'}>
                    {errors.name || 'A friendly name for this device'}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            </FormGroup>

            <Divider>
              <span style={{ fontSize: '0.75rem', color: C.subtle }}>Connection Settings</span>
            </Divider>

            {/* Host */}
            <FormGroup label="Host / IP Address" fieldId="device-host" isRequired>
              <TextInput
                id="device-host"
                value={formValues.host}
                onChange={(_event, value) => setField('host', value)}
                validated={errors.host ? 'error' : 'default'}
                placeholder="192.168.1.100 or inverter.local"
                required
                aria-label="Host or IP address"
              />
              <FormHelperText>
                <HelperText>
                  <HelperTextItem variant={errors.host ? 'error' : 'default'}>
                    {errors.host || 'Hostname or IP address of the Modbus device'}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            </FormGroup>

            {/* Port and Unit ID row */}
            <Flex gap={{ default: 'gapMd' }}>
              <FlexItem grow={{ default: 'grow' }}>
                <FormGroup label="Port" fieldId="device-port" isRequired>
                  <TextInput
                    id="device-port"
                    type="number"
                    value={String(formValues.port)}
                    onChange={(_event, value) => setField('port', value === '' ? '' : parseInt(value, 10))}
                    validated={errors.port ? 'error' : 'default'}
                    min={1}
                    max={65535}
                    required
                    aria-label="Modbus TCP port"
                  />
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant={errors.port ? 'error' : 'default'}>
                        {errors.port || 'Modbus TCP port'}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                </FormGroup>
              </FlexItem>
              <FlexItem grow={{ default: 'grow' }}>
                <FormGroup label="Unit ID" fieldId="device-unit-id" isRequired>
                  <TextInput
                    id="device-unit-id"
                    type="number"
                    value={String(formValues.unitId)}
                    onChange={(_event, value) => setField('unitId', value === '' ? '' : parseInt(value, 10))}
                    validated={errors.unitId ? 'error' : 'default'}
                    min={1}
                    max={247}
                    required
                    aria-label="Modbus slave unit ID"
                  />
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant={errors.unitId ? 'error' : 'default'}>
                        {errors.unitId || 'Modbus slave ID (1-247)'}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                </FormGroup>
              </FlexItem>
            </Flex>

            {/* Connection Test */}
            <Flex gap={{ default: 'gapMd' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>
                <ConnectionTestButton
                  onTest={handleTestConnection}
                  isTesting={isTesting}
                  disabled={!canTest}
                />
              </FlexItem>
              {testResult && (
                <FlexItem>
                  <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
                    <FlexItem>
                      {testResult.success
                        ? <CheckCircleIcon style={{ color: C.success }} />
                        : <ExclamationCircleIcon style={{ color: C.danger }} />
                      }
                    </FlexItem>
                    <FlexItem>
                      <span style={{ fontSize: '0.875rem', color: testResult.success ? C.success : C.danger }}>
                        {testResult.success ? 'Connection successful' : testResult.error}
                      </span>
                    </FlexItem>
                  </Flex>
                </FlexItem>
              )}
            </Flex>

            {/* Device info from test */}
            {testResult?.success && testResult.data?.manufacturer && (
              <Alert variant="success" isInline title="Device detected">
                <p style={{ fontSize: '0.875rem' }}>
                  <strong>Detected:</strong> {testResult.data.manufacturer}
                  {testResult.data.modelName && ` - ${testResult.data.modelName}`}
                </p>
                {testResult.data.detectionMethod && (
                  <p style={{ fontSize: '0.75rem', color: C.subtle }}>
                    Protocol: {testResult.data.detectionMethod} | Response: {testResult.data.responseTimeMs}ms
                  </p>
                )}
              </Alert>
            )}

            {testResult?.success && !testResult.data?.manufacturer && testResult.data?.detectionMethod && (
              <Alert variant="success" isInline title="Connection succeeded">
                <p style={{ fontSize: '0.875rem' }}>
                  <strong>Protocol:</strong> {testResult.data.detectionMethod}
                </p>
                {testResult.data.responseTimeMs && (
                  <p style={{ fontSize: '0.75rem', color: C.subtle }}>
                    Response: {testResult.data.responseTimeMs}ms
                  </p>
                )}
              </Alert>
            )}

            <Divider />

            {/* Enabled toggle */}
            <Flex justifyContent={{ default: 'justifyContentSpaceBetween' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>
                <div>
                  <div style={{ fontSize: '0.875rem' }}>Device Enabled</div>
                  <div style={{ fontSize: '0.75rem', color: C.subtle }}>
                    Disabled devices will not be monitored or polled
                  </div>
                </div>
              </FlexItem>
              <FlexItem>
                <Switch
                  id="device-enabled"
                  isChecked={formValues.enabled}
                  onChange={(_event, checked) => setField('enabled', checked)}
                  hasCheckIcon
                />
              </FlexItem>
            </Flex>

          </div>
        </form>
      </ModalBody>
      <ModalFooter>
        <Button
          variant="primary"
          form="device-form"
          type="submit"
          isDisabled={isSubmitting}
          icon={isSubmitting ? <Spinner size="sm" /> : undefined}
        >
          {isSubmitting ? 'Saving...' : isEditMode ? 'Save Changes' : 'Add Device'}
        </Button>
        <Button variant="link" onClick={onClose} isDisabled={isSubmitting}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
}

export default DeviceForm;
