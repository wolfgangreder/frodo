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

import React, { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  CardBody,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Spinner,
} from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  ExclamationTriangleIcon,
  MicrochipIcon,
} from '@patternfly/react-icons';
import { PageHeader } from '../components/common';
import {
  useGpioStatus,
  useGpioAssignments,
  useSetManualOutput,
  useClearManualOutput,
  useSetGpioAssignment,
  useDeleteGpioAssignment,
} from '../hooks/useGpio';
import { useDeviceList } from '../hooks';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #73bcf7)',
  success: 'var(--pf-t--global--icon--color--status--success--default, #5ba352)',
  warning: 'var(--pf-t--global--icon--color--status--warning--default, #f0ab00)',
  danger: 'var(--pf-t--global--icon--color--status--danger--default, #c9190b)',
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text--color--disabled, #6a6e73)',
};

/**
 * GPIO system status banner.
 */
function GpioSystemStatus({ status }) {
  if (!status) return null;

  return (
    <Card>
      <CardBody>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
          <span style={{ fontSize: '1.125rem', fontWeight: 600, color: C.primary }}>
            GPIO Export Control
          </span>
          {status.available ? (
            <Label color="green" icon={<CheckCircleIcon />}>Available</Label>
          ) : (
            <Label color="red" icon={<ExclamationCircleIcon />}>Unavailable</Label>
          )}
        </div>
        <div style={{ display: 'flex', gap: 24 }}>
          <span style={{ fontSize: '0.875rem', color: C.subtle }}>
            Platform: {status.platform || 'Unknown'}
          </span>
          <span style={{ fontSize: '0.875rem', color: C.subtle }}>
            Raspberry Pi: {status.isRaspberryPi ? 'Yes' : 'No'}
          </span>
          <span style={{ fontSize: '0.875rem', color: C.subtle }}>
            Pairs: {status.pairs?.length ?? 0}
          </span>
        </div>
        {status.errorMessage && (
          <Alert variant="danger" title={status.errorMessage} isInline style={{ marginTop: 8 }} />
        )}
      </CardBody>
    </Card>
  );
}

/**
 * Card for a single GPIO pair.
 */
function GpioPairCard({ pair, devices, onAssign, onUnassign }) {
  const setManual = useSetManualOutput();
  const clearManual = useClearManualOutput();

  const assignedDevice = devices?.find((d) => d.id === pair.assignedDeviceId);

  return (
    <Card>
      <CardBody>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16 }}>
          <MicrochipIcon style={{ color: pair.available ? C.primary : C.disabled }} />
          <span style={{ fontSize: '1.125rem', fontWeight: 600 }}>{pair.name}</span>
          {pair.available ? (
            <Label color="green" variant="outline">Ready</Label>
          ) : (
            <Label color="red" variant="outline">Unavailable</Label>
          )}
        </div>

        {pair.externalModeActive && (
          <Alert
            variant="warning"
            title="External override switch active — automatic control suspended"
            isInline
            style={{ marginBottom: 16 }}
          />
        )}

        {pair.outputManualOverride && (
          <Alert
            variant="info"
            title="Manual test mode — scheduler writes are suppressed"
            isInline
            style={{ marginBottom: 16 }}
          />
        )}

        {pair.errorMessage && (
          <Alert variant="danger" title={pair.errorMessage} isInline style={{ marginBottom: 16 }} />
        )}

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: 4 }}>Output Pin</div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ fontFamily: 'monospace' }}>GPIO {pair.outputPin}</span>
              {pair.outputPinState != null && (
                <Label color={pair.outputPinState ? 'red' : 'green'}>
                  {pair.outputPinState ? 'HIGH' : 'LOW'}
                </Label>
              )}
            </div>
          </div>
          <div>
            <div style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: 4 }}>Input Pin</div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ fontFamily: 'monospace' }}>GPIO {pair.inputPin}</span>
              {pair.inputPinState != null && (
                <Label color={pair.inputPinState ? 'orange' : 'grey'} variant="outline">
                  {pair.inputPinState ? 'HIGH' : 'LOW'}
                </Label>
              )}
              {pair.inputBias && (
                <Label color="grey" variant="outline">
                  {pair.inputBias.replace('_', ' ')}
                </Label>
              )}
            </div>
          </div>
          <div>
            <div style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: 4 }}>External Mode</div>
            <span>{pair.externalModeActive ? 'ACTIVE' : 'Inactive'}</span>
          </div>
        </div>

        {pair.available && (
          <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
            <Button
              size="sm"
              variant={pair.outputManualOverride ? 'primary' : 'secondary'}
              onClick={() => setManual.mutate({ name: pair.name, high: true })}
              isDisabled={setManual.isPending}
            >
              Set HIGH
            </Button>
            <Button
              size="sm"
              variant={pair.outputManualOverride ? 'primary' : 'secondary'}
              onClick={() => setManual.mutate({ name: pair.name, high: false })}
              isDisabled={setManual.isPending}
            >
              Set LOW
            </Button>
            {pair.outputManualOverride && (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => clearManual.mutate({ name: pair.name })}
                isDisabled={clearManual.isPending}
              >
                Clear Override
              </Button>
            )}
          </div>
        )}

        <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '0.875rem', color: C.subtle }}>Assigned device:</span>
          {assignedDevice ? (
            <>
              <span style={{ fontSize: '0.875rem' }}>
                {assignedDevice.name} (ID {assignedDevice.id})
              </span>
              <Button size="sm" variant="secondary" onClick={() => onAssign(pair.name)}>
                Change
              </Button>
              <Button size="sm" variant="danger" onClick={() => onUnassign(pair.assignedDeviceId)}>
                Remove
              </Button>
            </>
          ) : (
            <>
              <span style={{ fontSize: '0.875rem', color: C.subtle, fontStyle: 'italic' }}>
                (unassigned)
              </span>
              <Button size="sm" variant="secondary" onClick={() => onAssign(pair.name)}>
                Assign
              </Button>
            </>
          )}
        </div>
      </CardBody>
    </Card>
  );
}

/**
 * Modal to assign a GPIO pair to a device.
 */
function AssignmentDialog({ open, pairName, devices, assignments, onClose, onSave }) {
  const [selectedDeviceId, setSelectedDeviceId] = useState('');

  const assignedDeviceIds = (assignments ?? [])
    .filter((a) => a.gpioPairName !== pairName)
    .map((a) => a.deviceId);
  const availableDevices = (devices ?? []).filter(
    (d) => d.enabled && !assignedDeviceIds.includes(d.id)
  );

  const handleSave = () => {
    if (selectedDeviceId) {
      onSave(Number(selectedDeviceId), pairName);
      setSelectedDeviceId('');
    }
  };

  return (
    <Modal isOpen={open} onClose={onClose} variant="medium">
      <ModalHeader title={`Assign GPIO Pair "${pairName}" to Device`} />
      <ModalBody>
        <FormGroup label="Device" fieldId="gpio-device-select" style={{ marginTop: 8 }}>
          <FormSelect
            id="gpio-device-select"
            value={selectedDeviceId}
            onChange={(_event, value) => setSelectedDeviceId(value)}
            aria-label="Select device"
          >
            <FormSelectOption value="" label="— select a device —" isDisabled />
            {availableDevices.map((d) => (
              <FormSelectOption key={d.id} value={String(d.id)} label={`${d.name} (ID ${d.id})`} />
            ))}
          </FormSelect>
        </FormGroup>
        {availableDevices.length === 0 && (
          <Alert
            variant="info"
            title="No available devices. All enabled devices are already assigned to GPIO pairs."
            isInline
            style={{ marginTop: 16 }}
          />
        )}
      </ModalBody>
      <ModalFooter>
        <Button variant="primary" onClick={handleSave} isDisabled={!selectedDeviceId}>
          Assign
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
}

/**
 * GPIO management page.
 */
function GpioPage() {
  const { data: status, isLoading, error } = useGpioStatus();
  const { data: assignments } = useGpioAssignments();
  const { data: devices } = useDeviceList();
  const setAssignment = useSetGpioAssignment();
  const deleteAssignment = useDeleteGpioAssignment();

  const [assignDialog, setAssignDialog] = useState({ open: false, pairName: null });

  const handleAssign = (pairName) => {
    setAssignDialog({ open: true, pairName });
  };

  const handleUnassign = (deviceId) => {
    deleteAssignment.mutate({ deviceId });
  };

  const handleSaveAssignment = (deviceId, pairName) => {
    setAssignment.mutate({ deviceId, gpioPairName: pairName });
    setAssignDialog({ open: false, pairName: null });
  };

  return (
    <div>
      <PageHeader
        title="GPIO Export Control"
        subtitle="Manage GPIO-based export control relay pairs (Raspberry Pi)"
      />

      {isLoading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '32px 0' }}>
          <Spinner />
        </div>
      )}

      {error && (
        <Alert variant="danger" title={`Failed to load GPIO status: ${error.message}`} isInline style={{ marginBottom: 16 }} />
      )}

      {status && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <GpioSystemStatus status={status} />

          {status.pairs?.map((pair) => (
            <GpioPairCard
              key={pair.name}
              pair={pair}
              devices={devices}
              onAssign={handleAssign}
              onUnassign={handleUnassign}
            />
          ))}

          {(!status.pairs || status.pairs.length === 0) && (
            <Alert
              variant="info"
              title="No GPIO pairs configured. Add pairs in application.properties (frodo.gpio.pairs.<name>.*)."
              isInline
            />
          )}
        </div>
      )}

      <AssignmentDialog
        open={assignDialog.open}
        pairName={assignDialog.pairName}
        devices={devices}
        assignments={assignments}
        onClose={() => setAssignDialog({ open: false, pairName: null })}
        onSave={handleSaveAssignment}
      />
    </div>
  );
}

export default GpioPage;
