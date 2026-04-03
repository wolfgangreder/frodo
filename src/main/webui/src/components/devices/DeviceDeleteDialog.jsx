import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  CircularProgress,
  Typography,
  Box,
} from '@mui/material';
import WarningIcon from '@mui/icons-material/Warning';

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

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <WarningIcon color="error" />
        Delete Device
      </DialogTitle>

      <DialogContent>
        <DialogContentText>
          Are you sure you want to delete the device{' '}
          <Typography component="span" fontWeight="bold">
            "{device.name}"
          </Typography>
          ?
        </DialogContentText>
        <Box sx={{ mt: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
          <Typography variant="body2" color="text.secondary">
            <strong>Host:</strong> {device.host}:{device.port}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            <strong>Unit ID:</strong> {device.unitId}
          </Typography>
        </Box>
        <DialogContentText sx={{ mt: 2 }} color="error.main">
          This action cannot be undone. All historical data for this device will be permanently deleted.
        </DialogContentText>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={isDeleting}>
          Cancel
        </Button>
        <Button
          onClick={() => onConfirm(device.id)}
          color="error"
          variant="contained"
          disabled={isDeleting}
          startIcon={isDeleting && <CircularProgress size={20} color="inherit" />}
        >
          {isDeleting ? 'Deleting...' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default DeviceDeleteDialog;
