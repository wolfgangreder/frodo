import React from 'react';
import {
  Card,
  CardContent,
  CardActions,
  Typography,
  Box,
  Chip,
  IconButton,
  Tooltip,
  Divider,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
import RefreshIcon from '@mui/icons-material/Refresh';
import TimelineIcon from '@mui/icons-material/Timeline';
import DashboardIcon from '@mui/icons-material/Dashboard';
import RouterIcon from '@mui/icons-material/Router';

/**
 * Connection status chip component
 */
function StatusChip({ status }) {
  const statusConfig = {
    CONNECTED: { color: 'success', label: 'Connected' },
    DISCONNECTED: { color: 'error', label: 'Disconnected' },
    UNKNOWN: { color: 'default', label: 'Unknown' },
    CONNECTING: { color: 'warning', label: 'Connecting' },
  };

  const config = statusConfig[status] || statusConfig.UNKNOWN;

  return (
    <Chip
      label={config.label}
      color={config.color}
      size="small"
      variant="outlined"
    />
  );
}

/**
 * Device card component for mobile view
 * 
 * @param {Object} props
 * @param {Object} props.device - Device data
 * @param {Function} props.onEdit - Callback when edit is clicked
 * @param {Function} props.onDelete - Callback when delete is clicked
 * @param {Function} props.onViewInfo - Callback when view info is clicked
 * @param {Function} props.onRefreshInfo - Callback when refresh info is clicked
 * @param {Function} props.onMetrics - Callback when metrics is clicked
 * @param {Function} props.onDashboard - Callback when dashboard is clicked
 * @param {boolean} props.isRefreshing - Whether info is being refreshed
 */
function DeviceCard({
  device,
  onEdit,
  onDelete,
  onViewInfo,
  onRefreshInfo,
  onMetrics,
  onDashboard,
  isRefreshing = false,
}) {
  return (
    <Card
      sx={{
        bgcolor: 'background.paper',
        opacity: device.enabled ? 1 : 0.7,
      }}
    >
      <CardContent sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', mb: 2 }}>
          <RouterIcon
            sx={{
              fontSize: 40,
              color: device.enabled ? 'primary.main' : 'text.disabled',
              mr: 2,
            }}
          />
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6" component="div">
              {device.name}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {device.host}:{device.port} (Unit {device.unitId})
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, alignItems: 'flex-end' }}>
            <StatusChip status={device.connectionStatus} />
            <Chip
              label={device.enabled ? 'Enabled' : 'Disabled'}
              color={device.enabled ? 'primary' : 'default'}
              size="small"
              variant={device.enabled ? 'filled' : 'outlined'}
            />
          </Box>
        </Box>

        {device.manufacturer && (
          <Box sx={{ mt: 1 }}>
            <Typography variant="caption" color="text.secondary">
              {device.manufacturer}
              {device.modelName && ` - ${device.modelName}`}
            </Typography>
          </Box>
        )}
      </CardContent>

      <Divider />

      <CardActions sx={{ justifyContent: 'flex-end' }}>
        <Tooltip title="Device Dashboard">
          <IconButton
            size="small"
            onClick={() => onDashboard?.(device)}
            color="success"
          >
            <DashboardIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Metrics Configuration">
          <IconButton
            size="small"
            onClick={() => onMetrics?.(device)}
            color="warning"
          >
            <TimelineIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="View Device Info">
          <IconButton
            size="small"
            onClick={() => onViewInfo?.(device)}
            color="info"
          >
            <InfoIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Refresh Device Info">
          <IconButton
            size="small"
            onClick={() => onRefreshInfo?.(device)}
            disabled={isRefreshing}
            color="secondary"
          >
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Edit Device">
          <IconButton
            size="small"
            onClick={() => onEdit?.(device)}
            color="primary"
          >
            <EditIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Delete Device">
          <IconButton
            size="small"
            onClick={() => onDelete?.(device)}
            color="error"
          >
            <DeleteIcon />
          </IconButton>
        </Tooltip>
      </CardActions>
    </Card>
  );
}

export default DeviceCard;
