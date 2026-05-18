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
import { Label } from '@patternfly/react-core';

const STATUS_CONFIG = {
  CONNECTED: { color: 'green', label: 'Connected' },
  DISCONNECTED: { color: 'red', label: 'Disconnected' },
  UNKNOWN: { color: 'grey', label: 'Unknown' },
  CONNECTING: { color: 'orange', label: 'Connecting' },
};

/**
 * Connection status chip component
 *
 * @param {Object} props
 * @param {string} props.status - Connection status (CONNECTED, DISCONNECTED, UNKNOWN, CONNECTING)
 * @param {'sm'|'md'} [props.size='sm'] - Label size
 * @param {'filled'|'outline'} [props.variant='outline'] - Label variant
 */
function StatusChip({ status, size = 'sm', variant = 'outline' }) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.UNKNOWN;

  return (
    <Label
      color={config.color}
      variant={variant}
      aria-label={`Connection status: ${config.label}`}
    >
      {config.label}
    </Label>
  );
}

export default StatusChip;
