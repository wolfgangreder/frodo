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
import { StatusChip } from '../common';

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
            aria-label={`Open dashboard for ${device.name}`}
          >
            <DashboardIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Metrics Configuration">
          <IconButton
            size="small"
            onClick={() => onMetrics?.(device)}
            color="warning"
            aria-label={`Configure metrics for ${device.name}`}
          >
            <TimelineIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="View Device Info">
          <IconButton
            size="small"
            onClick={() => onViewInfo?.(device)}
            color="info"
            aria-label={`View info for ${device.name}`}
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
            aria-label={`Refresh info for ${device.name}`}
          >
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Edit Device">
          <IconButton
            size="small"
            onClick={() => onEdit?.(device)}
            color="primary"
            aria-label={`Edit ${device.name}`}
          >
            <EditIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Delete Device">
          <IconButton
            size="small"
            onClick={() => onDelete?.(device)}
            color="error"
            aria-label={`Delete ${device.name}`}
          >
            <DeleteIcon />
          </IconButton>
        </Tooltip>
      </CardActions>
    </Card>
  );
}

export default DeviceCard;
