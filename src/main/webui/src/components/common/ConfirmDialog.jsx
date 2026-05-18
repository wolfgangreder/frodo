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
} from '@patternfly/react-core';

/**
 * Generic confirmation dialog for destructive or important actions.
 *
 * @param {Object} props
 * @param {boolean} props.open - Whether dialog is open
 * @param {Function} props.onClose - Callback when dialog closes
 * @param {Function} props.onConfirm - Callback when confirmed
 * @param {string} props.title - Dialog title
 * @param {string|React.ReactNode} props.message - Confirmation message
 * @param {string} [props.confirmLabel='Confirm'] - Confirm button label
 * @param {string} [props.cancelLabel='Cancel'] - Cancel button label
 * @param {'danger'|'warning'|'primary'} [props.confirmColor='danger'] - Confirm button variant
 * @param {boolean} [props.isLoading=false] - Whether action is in progress
 * @param {boolean} [props.showWarningIcon=true] - Show warning icon in title
 * @param {React.ReactNode} [props.children] - Optional extra content below message
 */
function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title = 'Confirm Action',
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  confirmColor = 'danger',
  isLoading = false,
  showWarningIcon = true,
  children,
}) {
  // MUI uses 'error'; PF uses 'danger'
  const pfVariant = confirmColor === 'error' ? 'danger' : confirmColor;

  return (
    <Modal
      isOpen={open}
      onClose={isLoading ? undefined : onClose}
      variant="small"
      aria-labelledby="confirm-dialog-title"
      aria-describedby="confirm-dialog-description"
    >
      <ModalHeader
        title={title}
        labelId="confirm-dialog-title"
        titleIconVariant={showWarningIcon ? 'warning' : undefined}
      />
      <ModalBody id="confirm-dialog-description">
        {message && <p>{message}</p>}
        {children}
      </ModalBody>
      <ModalFooter>
        <Button
          variant={pfVariant}
          onClick={onConfirm}
          isDisabled={isLoading}
          icon={isLoading ? <Spinner size="sm" aria-label="Processing" /> : undefined}
        >
          {isLoading ? 'Processing...' : confirmLabel}
        </Button>
        <Button variant="link" onClick={onClose} isDisabled={isLoading}>
          {cancelLabel}
        </Button>
      </ModalFooter>
    </Modal>
  );
}

export default ConfirmDialog;
