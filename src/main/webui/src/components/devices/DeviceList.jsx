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
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Chip,
  Tooltip,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
import RefreshIcon from '@mui/icons-material/Refresh';
import TimelineIcon from '@mui/icons-material/Timeline';
import DashboardIcon from '@mui/icons-material/Dashboard';
import { StatusChip } from '../common';
import DeviceCard from './DeviceCard';

/**
 * Enabled status chip component
 */
function EnabledChip({ enabled }) {
  return (
    <Chip
      label={enabled ? 'Enabled' : 'Disabled'}
      color={enabled ? 'primary' : 'default'}
      size="small"
      variant={enabled ? 'filled' : 'outlined'}
    />
  );
}

/**
 * Device list component - displays devices in table (desktop) or cards (mobile)
 * 
 * @param {Object} props
 * @param {Array} props.devices - List of devices to display
 * @param {Function} props.onEdit - Callback when edit is clicked
 * @param {Function} props.onDelete - Callback when delete is clicked
 * @param {Function} props.onViewInfo - Callback when view info is clicked
 * @param {Function} props.onRefreshInfo - Callback when refresh info is clicked
 * @param {Function} props.onMetrics - Callback when metrics is clicked
 * @param {Function} props.onDashboard - Callback when dashboard is clicked
 * @param {boolean} props.isRefreshing - Whether info is being refreshed
 */
function DeviceList({
  devices = [],
  onEdit,
  onDelete,
  onViewInfo,
  onRefreshInfo,
  onMetrics,
  onDashboard,
  isRefreshing = false,
}) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  // Mobile view - use cards
  if (isMobile) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {devices.map((device) => (
          <DeviceCard
            key={device.id}
            device={device}
            onEdit={onEdit}
            onDelete={onDelete}
            onViewInfo={onViewInfo}
            onRefreshInfo={onRefreshInfo}
            onMetrics={onMetrics}
            onDashboard={onDashboard}
            isRefreshing={isRefreshing}
          />
        ))}
      </Box>
    );
  }

  // Desktop view - use table
  return (
    <TableContainer component={Paper} sx={{ bgcolor: 'background.paper' }}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Host</TableCell>
            <TableCell align="center">Port</TableCell>
            <TableCell align="center">Unit ID</TableCell>
            <TableCell align="center">Status</TableCell>
            <TableCell align="center">Enabled</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {devices.map((device) => (
            <TableRow
              key={device.id}
              hover
              sx={{ '&:last-child td, &:last-child th': { border: 0 } }}
            >
              <TableCell component="th" scope="row">
                {device.name}
              </TableCell>
              <TableCell>{device.host}</TableCell>
              <TableCell align="center">{device.port}</TableCell>
              <TableCell align="center">{device.unitId}</TableCell>
              <TableCell align="center">
                <StatusChip status={device.connectionStatus} />
              </TableCell>
              <TableCell align="center">
                <EnabledChip enabled={device.enabled} />
              </TableCell>
              <TableCell align="right">
                <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                  <Tooltip title="Device Dashboard">
                    <IconButton
                      size="small"
                      onClick={() => onDashboard?.(device)}
                      color="success"
                      aria-label={`Open dashboard for ${device.name}`}
                    >
                      <DashboardIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Metrics Configuration">
                    <IconButton
                      size="small"
                      onClick={() => onMetrics?.(device)}
                      color="warning"
                      aria-label={`Configure metrics for ${device.name}`}
                    >
                      <TimelineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="View Device Info">
                    <IconButton
                      size="small"
                      onClick={() => onViewInfo?.(device)}
                      color="info"
                      aria-label={`View info for ${device.name}`}
                    >
                      <InfoIcon fontSize="small" />
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
                      <RefreshIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Edit Device">
                    <IconButton
                      size="small"
                      onClick={() => onEdit?.(device)}
                      color="primary"
                      aria-label={`Edit ${device.name}`}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete Device">
                    <IconButton
                      size="small"
                      onClick={() => onDelete?.(device)}
                      color="error"
                      aria-label={`Delete ${device.name}`}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default DeviceList;
