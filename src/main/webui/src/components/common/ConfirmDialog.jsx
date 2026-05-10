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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  CircularProgress,
} from '@mui/material';
import WarningIcon from '@mui/icons-material/Warning';

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
 * @param {'error'|'warning'|'primary'} [props.confirmColor='error'] - Confirm button color
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
  confirmColor = 'error',
  isLoading = false,
  showWarningIcon = true,
  children,
}) {
  return (
    <Dialog
      open={open}
      onClose={isLoading ? undefined : onClose}
      maxWidth="xs"
      fullWidth
      aria-labelledby="confirm-dialog-title"
      aria-describedby="confirm-dialog-description"
    >
      <DialogTitle
        id="confirm-dialog-title"
        sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
      >
        {showWarningIcon && <WarningIcon color={confirmColor} />}
        {title}
      </DialogTitle>

      <DialogContent>
        {message && (
          <DialogContentText id="confirm-dialog-description">
            {message}
          </DialogContentText>
        )}
        {children}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={isLoading}>
          {cancelLabel}
        </Button>
        <Button
          onClick={onConfirm}
          color={confirmColor}
          variant="contained"
          disabled={isLoading}
          startIcon={
            isLoading ? <CircularProgress size={20} color="inherit" /> : null
          }
        >
          {isLoading ? 'Processing...' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default ConfirmDialog;
