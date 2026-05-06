import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import MemoryIcon from '@mui/icons-material/Memory';
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

/**
 * GPIO system status banner.
 */
function GpioSystemStatus({ status }) {
  if (!status) return null;

  return (
    <Card>
      <CardContent>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6" sx={{ color: 'primary.main' }}>
            GPIO Export Control
          </Typography>
          {status.available ? (
            <Chip icon={<CheckCircleOutlineIcon />} label="Available" color="success" size="small" />
          ) : (
            <Chip icon={<ErrorOutlineIcon />} label="Unavailable" color="error" size="small" />
          )}
        </Stack>
        <Stack direction="row" spacing={3}>
          <Typography variant="body2" color="text.secondary">
            Platform: {status.platform || 'Unknown'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Raspberry Pi 5: {status.isRaspberryPi5 ? 'Yes' : 'No'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Pairs: {status.pairs?.length ?? 0}
          </Typography>
        </Stack>
        {status.errorMessage && (
          <Alert severity="error" sx={{ mt: 1 }}>
            {status.errorMessage}
          </Alert>
        )}
      </CardContent>
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
      <CardContent>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
          <MemoryIcon color={pair.available ? 'primary' : 'disabled'} />
          <Typography variant="h6">
            {pair.name}
          </Typography>
          {pair.available ? (
            <Chip label="Ready" color="success" size="small" variant="outlined" />
          ) : (
            <Chip label="Unavailable" color="error" size="small" variant="outlined" />
          )}
        </Stack>

        {/* External mode warning */}
        {pair.externalModeActive && (
          <Alert severity="warning" icon={<WarningAmberIcon />} sx={{ mb: 2 }}>
            External override switch active — automatic control suspended
          </Alert>
        )}

        {/* Manual override info */}
        {pair.outputManualOverride && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Manual test mode — scheduler writes are suppressed
          </Alert>
        )}

        {pair.errorMessage && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {pair.errorMessage}
          </Alert>
        )}

        {/* Pin states */}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ mb: 2 }}>
          <Box>
            <Typography variant="body2" color="text.secondary">Output Pin</Typography>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                GPIO {pair.outputPin}
              </Typography>
              {pair.outputPinState != null && (
                <Chip
                  label={pair.outputPinState ? 'HIGH' : 'LOW'}
                  color={pair.outputPinState ? 'error' : 'success'}
                  size="small"
                />
              )}
            </Stack>
          </Box>
          <Box>
            <Typography variant="body2" color="text.secondary">Input Pin</Typography>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                GPIO {pair.inputPin}
              </Typography>
              {pair.inputPinState != null && (
                <Chip
                  label={pair.inputPinState ? 'HIGH' : 'LOW'}
                  color={pair.inputPinState ? 'warning' : 'default'}
                  size="small"
                  variant="outlined"
                />
              )}
              {pair.inputBias && (
                <Chip
                  label={pair.inputBias.replace('_', ' ')}
                  size="small"
                  variant="outlined"
                  color="default"
                />
              )}
            </Stack>
          </Box>
          <Box>
            <Typography variant="body2" color="text.secondary">External Mode</Typography>
            <Typography variant="body1">
              {pair.externalModeActive ? 'ACTIVE' : 'Inactive'}
            </Typography>
          </Box>
        </Stack>

        {/* Manual output controls */}
        {pair.available && (
          <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
            <Button
              size="small"
              variant={pair.outputManualOverride ? 'contained' : 'outlined'}
              color="warning"
              onClick={() => setManual.mutate({ name: pair.name, high: true })}
              disabled={setManual.isPending}
            >
              Set HIGH
            </Button>
            <Button
              size="small"
              variant={pair.outputManualOverride ? 'contained' : 'outlined'}
              color="info"
              onClick={() => setManual.mutate({ name: pair.name, high: false })}
              disabled={setManual.isPending}
            >
              Set LOW
            </Button>
            {pair.outputManualOverride && (
              <Button
                size="small"
                variant="outlined"
                onClick={() => clearManual.mutate({ name: pair.name })}
                disabled={clearManual.isPending}
              >
                Clear Override
              </Button>
            )}
          </Stack>
        )}

        {/* Device assignment */}
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="body2" color="text.secondary">
            Assigned device:
          </Typography>
          {assignedDevice ? (
            <>
              <Typography variant="body2">
                {assignedDevice.name} (ID {assignedDevice.id})
              </Typography>
              <Button size="small" variant="outlined" onClick={() => onAssign(pair.name)}>
                Change
              </Button>
              <Button
                size="small"
                variant="outlined"
                color="error"
                onClick={() => onUnassign(pair.assignedDeviceId)}
              >
                Remove
              </Button>
            </>
          ) : (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                (unassigned)
              </Typography>
              <Button size="small" variant="outlined" onClick={() => onAssign(pair.name)}>
                Assign
              </Button>
            </>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

/**
 * Dialog to assign a GPIO pair to a device.
 */
function AssignmentDialog({ open, pairName, devices, assignments, onClose, onSave }) {
  const [selectedDeviceId, setSelectedDeviceId] = useState('');

  // Filter out devices already assigned to other pairs
  const assignedDeviceIds = (assignments ?? [])
    .filter((a) => a.gpioPairName !== pairName)
    .map((a) => a.deviceId);
  const availableDevices = (devices ?? []).filter(
    (d) => d.enabled && !assignedDeviceIds.includes(d.id)
  );

  const handleSave = () => {
    if (selectedDeviceId) {
      onSave(selectedDeviceId, pairName);
      setSelectedDeviceId('');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Assign GPIO Pair "{pairName}" to Device</DialogTitle>
      <DialogContent>
        <FormControl fullWidth sx={{ mt: 1 }}>
          <InputLabel>Device</InputLabel>
          <Select
            value={selectedDeviceId}
            label="Device"
            onChange={(e) => setSelectedDeviceId(e.target.value)}
          >
            {availableDevices.map((d) => (
              <MenuItem key={d.id} value={d.id}>
                {d.name} (ID {d.id})
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {availableDevices.length === 0 && (
          <Alert severity="info" sx={{ mt: 2 }}>
            No available devices. All enabled devices are already assigned to GPIO pairs.
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={!selectedDeviceId}
        >
          Assign
        </Button>
      </DialogActions>
    </Dialog>
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
    <Box>
      <PageHeader
        title="GPIO Export Control"
        subtitle="Manage GPIO-based export control relay pairs (Raspberry Pi 5 only)"
      />

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load GPIO status: {error.message}
        </Alert>
      )}

      {status && (
        <Stack spacing={3}>
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
            <Alert severity="info">
              No GPIO pairs configured. Add pairs in application.properties
              (frodo.gpio.pairs.&lt;name&gt;.*).
            </Alert>
          )}
        </Stack>
      )}

      <AssignmentDialog
        open={assignDialog.open}
        pairName={assignDialog.pairName}
        devices={devices}
        assignments={assignments}
        onClose={() => setAssignDialog({ open: false, pairName: null })}
        onSave={handleSaveAssignment}
      />
    </Box>
  );
}

export default GpioPage;
