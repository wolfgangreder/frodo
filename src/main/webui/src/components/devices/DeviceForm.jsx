import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControlLabel,
  Switch,
  Box,
  Alert,
  CircularProgress,
  Typography,
  Divider,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ConnectionTestButton from './ConnectionTestButton';

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
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  
  const [formValues, setFormValues] = useState(defaultFormValues);
  const [errors, setErrors] = useState({});
  const [testResult, setTestResult] = useState(null);
  const [isTesting, setIsTesting] = useState(false);

  const isEditMode = !!device;

  // Reset form when dialog opens/closes or device changes
  useEffect(() => {
    if (open) {
      if (device) {
        setFormValues({
          name: device.name || '',
          host: device.host || '',
          port: device.port || 502,
          unitId: device.unitId || 1,
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

  /**
   * Handle input change
   */
  const handleChange = (event) => {
    const { name, value, type, checked } = event.target;
    setFormValues((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    // Clear error for this field
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: null }));
    }
  };

  /**
   * Handle number input change
   */
  const handleNumberChange = (event) => {
    const { name, value } = event.target;
    const numValue = value === '' ? '' : parseInt(value, 10);
    setFormValues((prev) => ({
      ...prev,
      [name]: numValue,
    }));
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: null }));
    }
  };

  /**
   * Validate form
   */
  const validate = () => {
    const newErrors = {};

    if (!formValues.name.trim()) {
      newErrors.name = 'Name is required';
    }

    if (!formValues.host.trim()) {
      newErrors.host = 'Host is required';
    }

    if (!formValues.port || formValues.port < 1 || formValues.port > 65535) {
      newErrors.port = 'Port must be between 1 and 65535';
    }

    if (formValues.unitId === '' || formValues.unitId < 1 || formValues.unitId > 247) {
      newErrors.unitId = 'Unit ID must be between 1 and 247';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  /**
   * Handle form submission
   */
  const handleSubmit = (event) => {
    event.preventDefault();
    if (validate()) {
      onSubmit({
        ...formValues,
        port: parseInt(formValues.port, 10),
        unitId: parseInt(formValues.unitId, 10),
      });
    }
  };

  /**
   * Handle connection test
   */
  const handleTestConnection = async () => {
    // Validate connection fields first
    const connectionErrors = {};
    if (!formValues.host.trim()) {
      connectionErrors.host = 'Host is required for testing';
    }
    if (!formValues.port || formValues.port < 1 || formValues.port > 65535) {
      connectionErrors.port = 'Valid port required for testing';
    }
    if (formValues.unitId === '' || formValues.unitId < 1 || formValues.unitId > 247) {
      connectionErrors.unitId = 'Valid Unit ID required for testing';
    }

    if (Object.keys(connectionErrors).length > 0) {
      setErrors((prev) => ({ ...prev, ...connectionErrors }));
      return;
    }

    setIsTesting(true);
    setTestResult(null);

    try {
      const result = await onTestConnection({
        host: formValues.host,
        port: parseInt(formValues.port, 10),
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

  /**
   * Check if connection params are valid for testing
   */
  const canTest = formValues.host.trim() && 
    formValues.port >= 1 && formValues.port <= 65535 &&
    formValues.unitId >= 1 && formValues.unitId <= 247;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullScreen={fullScreen}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        component: 'form',
        onSubmit: handleSubmit,
      }}
    >
      <DialogTitle>
        {isEditMode ? 'Edit Device' : 'Add New Device'}
      </DialogTitle>

      <DialogContent dividers>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pt: 1 }}>
          {/* Device Name */}
          <TextField
            name="name"
            label="Device Name"
            value={formValues.name}
            onChange={handleChange}
            error={!!errors.name}
            helperText={errors.name || 'A friendly name for this device'}
            fullWidth
            required
            autoFocus
          />

          <Divider>
            <Typography variant="caption" color="text.secondary">
              Connection Settings
            </Typography>
          </Divider>

          {/* Host */}
          <TextField
            name="host"
            label="Host / IP Address"
            value={formValues.host}
            onChange={handleChange}
            error={!!errors.host}
            helperText={errors.host || 'Hostname or IP address of the Modbus device'}
            fullWidth
            required
            placeholder="192.168.1.100 or inverter.local"
          />

          {/* Port and Unit ID row */}
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              name="port"
              label="Port"
              type="number"
              value={formValues.port}
              onChange={handleNumberChange}
              error={!!errors.port}
              helperText={errors.port || 'Modbus TCP port'}
              sx={{ flex: 1 }}
              required
              inputProps={{ min: 1, max: 65535 }}
            />
            <TextField
              name="unitId"
              label="Unit ID"
              type="number"
              value={formValues.unitId}
              onChange={handleNumberChange}
              error={!!errors.unitId}
              helperText={errors.unitId || 'Modbus slave ID (1-247)'}
              sx={{ flex: 1 }}
              required
              inputProps={{ min: 1, max: 247 }}
            />
          </Box>

          {/* Connection Test */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <ConnectionTestButton
              onTest={handleTestConnection}
              isTesting={isTesting}
              disabled={!canTest}
            />
            {testResult && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {testResult.success ? (
                  <>
                    <CheckCircleIcon color="success" />
                    <Typography variant="body2" color="success.main">
                      Connection successful
                    </Typography>
                  </>
                ) : (
                  <>
                    <ErrorIcon color="error" />
                    <Typography variant="body2" color="error.main">
                      {testResult.error}
                    </Typography>
                  </>
                )}
              </Box>
            )}
          </Box>

          {/* Device Info from test */}
          {testResult?.success && testResult.data?.manufacturer && (
            <Alert severity="success" sx={{ mt: 1 }}>
              <Typography variant="body2">
                <strong>Detected:</strong> {testResult.data.manufacturer}
                {testResult.data.modelName && ` - ${testResult.data.modelName}`}
              </Typography>
              {testResult.data.detectionMethod && (
                <Typography variant="caption" color="text.secondary" display="block">
                  Protocol: {testResult.data.detectionMethod} | Response: {testResult.data.responseTimeMs}ms
                </Typography>
              )}
            </Alert>
          )}

          {/* Successful connection without manufacturer info */}
          {testResult?.success && !testResult.data?.manufacturer && testResult.data?.detectionMethod && (
            <Alert severity="success" sx={{ mt: 1 }}>
              <Typography variant="body2">
                <strong>Protocol:</strong> {testResult.data.detectionMethod}
              </Typography>
              {testResult.data.responseTimeMs && (
                <Typography variant="caption" color="text.secondary" display="block">
                  Response: {testResult.data.responseTimeMs}ms
                </Typography>
              )}
            </Alert>
          )}

          <Divider />

          {/* Enabled toggle */}
          <FormControlLabel
            control={
              <Switch
                name="enabled"
                checked={formValues.enabled}
                onChange={handleChange}
                color="primary"
              />
            }
            label="Device Enabled"
          />
          <Typography variant="caption" color="text.secondary" sx={{ mt: -2 }}>
            Disabled devices will not be monitored or polled
          </Typography>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button
          type="submit"
          variant="contained"
          disabled={isSubmitting}
          startIcon={isSubmitting && <CircularProgress size={20} />}
        >
          {isSubmitting ? 'Saving...' : isEditMode ? 'Save Changes' : 'Add Device'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default DeviceForm;
