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

import React from 'react';
import {
  Modal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Spinner,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { ExclamationTriangleIcon } from '@patternfly/react-icons';

/**
 * Device delete confirmation dialog
 *
 * @param {Object} props
 * @param {boolean} props.open - Whether dialog is open
 * @param {Function} props.onClose - Callback when dialog closes
 * @param {Function} props.onConfirm - Callback when delete is confirmed
 * @param {Object} props.device - Device to delete
 * @param {boolean} props.isDeleting - Whether deletion is in progress
 */
function DeviceDeleteDialog({
  open,
  onClose,
  onConfirm,
  device,
  isDeleting = false,
}) {
  if (!device) return null;

  const C = {
    danger:  'var(--pf-t--global--color--status--danger--default, #c9190b)',
    subtle:  'var(--pf-t--global--text-color--subtle, #6a6e73)',
  };

  return (
    <Modal
      isOpen={open}
      onClose={onClose}
      variant="small"
      aria-labelledby="delete-device-title"
    >
      <ModalHeader
        title={
          <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
            <FlexItem>
              <ExclamationTriangleIcon style={{ color: C.danger }} />
            </FlexItem>
            <FlexItem id="delete-device-title">Delete Device</FlexItem>
          </Flex>
        }
      />
      <ModalBody>
        <p>
          Are you sure you want to delete the device{' '}
          <strong>"{device.name}"</strong>?
        </p>
        <div
          style={{
            marginTop: '1rem',
            padding: '0.75rem',
            background: 'var(--pf-t--global--background--color--secondary--default, #f0f0f0)',
            borderRadius: '4px',
          }}
        >
          <p style={{ fontSize: '0.875rem', color: C.subtle, margin: '0.25rem 0' }}>
            <strong>Host:</strong> {device.host}:{device.port}
          </p>
          <p style={{ fontSize: '0.875rem', color: C.subtle, margin: '0.25rem 0' }}>
            <strong>Unit ID:</strong> {device.unitId}
          </p>
        </div>
        <p style={{ marginTop: '1rem', color: C.danger, fontSize: '0.875rem' }}>
          This action cannot be undone. All historical data for this device will be permanently deleted.
        </p>
      </ModalBody>
      <ModalFooter>
        <Button variant="danger" onClick={() => onConfirm(device.id)} isDisabled={isDeleting} icon={isDeleting ? <Spinner size="sm" /> : undefined}>
          {isDeleting ? 'Deleting...' : 'Delete'}
        </Button>
        <Button variant="link" onClick={onClose} isDisabled={isDeleting}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
}

export default DeviceDeleteDialog;
