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
  Box,
  Typography,
  Chip,
  Skeleton,
  Stack,
  Divider,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import HelpIcon from '@mui/icons-material/Help';

/**
 * Formats a relative time string from an ISO timestamp or Instant string
 */
function formatTimeAgo(timestamp) {
  if (!timestamp) return 'Never';
  const now = Date.now();
  const then = new Date(timestamp).getTime();
  const diffSec = Math.floor((now - then) / 1000);
  if (diffSec < 10) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

/**
 * DeviceStatusCard - shows device identity and connection status.
 *
 * Always shows device entity data (name, connection) from the device prop.
 * SunSpec-derived data (manufacturer, model, serial, firmware) is layered on
 * top when the device is online.  When offline, a clear "Offline" badge and
 * message are shown without hiding the entity info.
 *
 * @param {Object} props
 * @param {Object} props.device       - Device entity (name, host, port, unitId, enabled)
 * @param {Object} props.commonData   - SunSpec Common model response (fields: Mn, Md, SN, Vr)
 * @param {Object} props.inverterData - Inverter model response (for readTime)
 * @param {boolean} props.isLoading   - Whether data is still loading
 * @param {boolean} props.isError     - Device unreachable (Modbus/SunSpec discovery failed)
 */
function DeviceStatusCard({ device, commonData, inverterData, isLoading, isError }) {
  const fields = commonData?.fields || {};
  const manufacturer = fields.Mn || null;
  const model = fields.Md || null;
  const serial = fields.SN || null;
  const firmware = fields.Vr || null;
  const lastRead = inverterData?.readTime || commonData?.readTime || null;

  const isOnline = !!inverterData && !isError;

  const statusChip = isLoading ? (
    <Chip label="Connecting…" color="default" size="small" variant="outlined" />
  ) : isError ? (
    <Chip
      icon={<WifiOffIcon sx={{ fontSize: 16 }} />}
      label="Offline"
      color="warning"
      size="small"
      variant="outlined"
    />
  ) : isOnline ? (
    <Chip
      icon={<CheckCircleIcon sx={{ fontSize: 16 }} />}
      label="Online"
      color="success"
      size="small"
      variant="outlined"
    />
  ) : (
    <Chip
      icon={<HelpIcon sx={{ fontSize: 16 }} />}
      label="Unknown"
      color="default"
      size="small"
      variant="outlined"
    />
  );

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Typography variant="h6" sx={{ color: 'primary.main' }}>
            Device Status
          </Typography>
          {statusChip}
        </Stack>

        {isLoading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {[...Array(4)].map((_, i) => (
              <Skeleton key={i} variant="text" width="100%" height={24} />
            ))}
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
            {/* Device entity info — always visible */}
            <InfoRow label="Name" value={device?.name} />
            <InfoRow
              label="Connection"
              value={`${device?.host}:${device?.port} unit ${device?.unitId}`}
            />

            {isError ? (
              <>
                <Divider sx={{ my: 0.5 }} />
                <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                  Modbus unreachable — SunSpec data unavailable
                </Typography>
              </>
            ) : (
              <>
                {/* SunSpec identification data — shown when online */}
                {(manufacturer || model || serial || firmware) && (
                  <Divider sx={{ my: 0.5 }} />
                )}
                {manufacturer && <InfoRow label="Manufacturer" value={manufacturer} />}
                {model && <InfoRow label="Model" value={model} />}
                {serial && <InfoRow label="Serial" value={serial} />}
                {firmware && <InfoRow label="Firmware" value={firmware} />}
                <Divider sx={{ my: 0.5 }} />
                <InfoRow label="Last Read" value={formatTimeAgo(lastRead)} />
              </>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

function InfoRow({ label, value }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 500, textAlign: 'right', maxWidth: '60%' }}>
        {value ?? '-'}
      </Typography>
    </Box>
  );
}

export default DeviceStatusCard;
