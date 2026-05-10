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
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import PauseCircleIcon from '@mui/icons-material/PauseCircle';
import HelpOutlineIcon from '@mui/icons-material/HelpOutlined';

/**
 * Format a timestamp for display
 */
function formatTime(isoString) {
  if (!isoString) return 'Never';
  const date = new Date(isoString);
  return date.toLocaleString();
}

/**
 * Format time ago
 */
function formatTimeAgo(isoString) {
  if (!isoString) return '';
  const seconds = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

/**
 * MetricsStatusCard - displays current scraping status
 *
 * @param {Object} props
 * @param {Object} props.status - Status data from API
 * @param {boolean} props.isLoading - Whether status is loading
 */
function MetricsStatusCard({ status, isLoading = false }) {
  if (isLoading || !status) {
    return null;
  }

  if (!status.configured) {
    return (
      <Alert severity="info" sx={{ mb: 2 }}>
        Metrics scraping is not yet configured for this device. Configure parameters below to start collecting data.
      </Alert>
    );
  }

  const statusIcon = () => {
    if (!status.enabled) return <PauseCircleIcon color="action" />;
    switch (status.lastScrapeStatus) {
      case 'SUCCESS':
        return <CheckCircleIcon color="success" />;
      case 'FAILED':
      case 'TIMEOUT':
        return <ErrorIcon color="error" />;
      default:
        return <HelpOutlineIcon color="action" />;
    }
  };

  const statusColor = () => {
    if (!status.enabled) return 'default';
    switch (status.lastScrapeStatus) {
      case 'SUCCESS':
        return 'success';
      case 'FAILED':
      case 'TIMEOUT':
        return 'error';
      default:
        return 'default';
    }
  };

  const statusLabel = () => {
    if (!status.enabled) return 'Paused';
    if (!status.lastScrapeStatus) return 'Waiting';
    return status.lastScrapeStatus;
  };

  return (
    <Card variant="outlined" sx={{ mb: 2 }}>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ flexGrow: 1 }}>
            {statusIcon()}
            <Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="subtitle2">Scraping Status</Typography>
                <Chip
                  label={statusLabel()}
                  size="small"
                  color={statusColor()}
                  variant={status.enabled ? 'filled' : 'outlined'}
                />
              </Stack>
              {status.lastScrapeTime && (
                <Typography variant="caption" color="text.secondary">
                  Last scrape: {formatTime(status.lastScrapeTime)} ({formatTimeAgo(status.lastScrapeTime)})
                </Typography>
              )}
            </Box>
          </Stack>

          <Stack direction="row" spacing={2}>
            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="h6" color="primary">
                {status.enabledParameterCount || 0}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Parameters
              </Typography>
            </Box>
            {status.scrapeIntervalSeconds && (
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h6" color="primary">
                  {status.scrapeIntervalSeconds}s
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Interval
                </Typography>
              </Box>
            )}
          </Stack>
        </Stack>

        {status.lastScrapeStatus === 'FAILED' && status.lastErrorMessage && (
          <Alert severity="error" sx={{ mt: 1 }} variant="outlined">
            {status.lastErrorMessage}
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

export default MetricsStatusCard;
