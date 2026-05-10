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
import { Chip } from '@mui/material';

const STATUS_CONFIG = {
  CONNECTED: { color: 'success', label: 'Connected' },
  DISCONNECTED: { color: 'error', label: 'Disconnected' },
  UNKNOWN: { color: 'default', label: 'Unknown' },
  CONNECTING: { color: 'warning', label: 'Connecting' },
};

/**
 * Connection status chip component
 *
 * @param {Object} props
 * @param {string} props.status - Connection status (CONNECTED, DISCONNECTED, UNKNOWN, CONNECTING)
 * @param {'small'|'medium'} [props.size='small'] - Chip size
 * @param {'outlined'|'filled'} [props.variant='outlined'] - Chip variant
 */
function StatusChip({ status, size = 'small', variant = 'outlined' }) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.UNKNOWN;

  return (
    <Chip
      label={config.label}
      color={config.color}
      size={size}
      variant={variant}
      aria-label={`Connection status: ${config.label}`}
    />
  );
}

export default StatusChip;
