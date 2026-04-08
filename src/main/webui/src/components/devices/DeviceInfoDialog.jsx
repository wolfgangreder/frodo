import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Divider,
  Chip,
  CircularProgress,
} from '@mui/material';
import InfoIcon from '@mui/icons-material/Info';
import RefreshIcon from '@mui/icons-material/Refresh';

/**
 * Device info dialog showing device identification details
 * 
 * @param {Object} props
 * @param {boolean} props.open - Whether dialog is open
 * @param {Function} props.onClose - Callback when dialog closes
 * @param {Function} props.onRefresh - Callback to refresh device info
 * @param {Object} props.device - Device data
 * @param {Object} props.deviceInfo - Device identification info (FC 0x2B result)
 * @param {boolean} props.isLoading - Whether info is loading
 * @param {boolean} props.isRefreshing - Whether info is being refreshed
 */
function DeviceInfoDialog({
  open,
  onClose,
  onRefresh,
  device,
  deviceInfo,
  isLoading = false,
  isRefreshing = false,
}) {
  if (!device) return null;

  /**
   * Info row component
   */
  const InfoRow = ({ label, value }) => (
    <Box sx={{ display: 'flex', py: 1 }}>
      <Typography
        variant="body2"
        color="text.secondary"
        sx={{ width: 140, flexShrink: 0 }}
      >
        {label}
      </Typography>
      <Typography variant="body2">
        {value || <em style={{ color: 'gray' }}>Not available</em>}
      </Typography>
    </Box>
  );

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <InfoIcon color="info" />
        Device Information
      </DialogTitle>

      <DialogContent dividers>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <Box>
            {/* Basic Info */}
            <Typography variant="subtitle2" color="primary" gutterBottom>
              Configuration
            </Typography>
            <InfoRow label="Name" value={device.name} />
            <InfoRow label="Host" value={`${device.host}:${device.port}`} />
            <InfoRow label="Unit ID" value={device.unitId} />
            <InfoRow
              label="Status"
              value={
                <Chip
                  label={device.enabled ? 'Enabled' : 'Disabled'}
                  color={device.enabled ? 'primary' : 'default'}
                  size="small"
                />
              }
            />

            <Divider sx={{ my: 2 }} />

            {/* Device Identification (Modbus FC 0x2B) */}
            <Typography variant="subtitle2" color="primary" gutterBottom>
              Device Identification
            </Typography>
            
            {deviceInfo ? (
              <>
                <InfoRow label="Vendor" value={deviceInfo.vendorName} />
                <InfoRow label="Product Code" value={deviceInfo.productCode} />
                <InfoRow label="Revision" value={deviceInfo.revision} />
                <InfoRow label="Vendor URL" value={deviceInfo.vendorUrl} />
                <InfoRow label="Product Name" value={deviceInfo.productName} />
                <InfoRow label="Model Name" value={deviceInfo.modelName} />
                <InfoRow label="User App Name" value={deviceInfo.userApplicationName} />
                
                {deviceInfo.lastUpdated && (
                  <Typography variant="caption" color="text.secondary" sx={{ mt: 2, display: 'block' }}>
                    Last updated: {new Date(deviceInfo.lastUpdated).toLocaleString()}
                  </Typography>
                )}
              </>
            ) : (
              <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                No device identification data available. Click "Refresh" to fetch from device.
              </Typography>
            )}
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button
          onClick={() => onRefresh(device.id)}
          disabled={isRefreshing}
          startIcon={
            isRefreshing ? (
              <CircularProgress size={20} />
            ) : (
              <RefreshIcon />
            )
          }
        >
          {isRefreshing ? 'Refreshing...' : 'Refresh'}
        </Button>
        <Button onClick={onClose} variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default DeviceInfoDialog;
