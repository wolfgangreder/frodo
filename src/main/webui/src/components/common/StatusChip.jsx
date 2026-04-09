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
