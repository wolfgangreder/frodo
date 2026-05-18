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
import { Alert, AlertActionCloseButton } from '@patternfly/react-core';

/**
 * Simplified NotificationSnackbar for Storybook demonstration.
 * The actual component uses a Zustand store for queue management and renders
 * a PF Alert in a fixed bottom-right container.
 */
function NotificationSnackbarDemo({ open, message, variant, onClose }) {
  if (!open) return <span style={{ color: 'var(--pf-t--global--text--color--subtle)' }}>(notification hidden)</span>;
  return (
    <div style={{ minWidth: 320, maxWidth: 480 }}>
      <Alert
        variant={variant}
        title={message}
        actionClose={<AlertActionCloseButton onClose={onClose} />}
      />
    </div>
  );
}

export default {
  title: 'Common/NotificationSnackbar',
  component: NotificationSnackbarDemo,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    open: {
      control: 'boolean',
      description: 'Whether notification is visible',
    },
    message: {
      control: 'text',
      description: 'Notification message',
    },
    variant: {
      control: 'select',
      options: ['success', 'info', 'warning', 'danger'],
      description: 'Notification variant',
    },
  },
};

export const Success = {
  args: {
    open: true,
    message: 'Device saved successfully',
    variant: 'success',
  },
};

export const Info = {
  args: {
    open: true,
    message: 'Metrics scraping started',
    variant: 'info',
  },
};

export const Warning = {
  args: {
    open: true,
    message: 'Connection timeout - retrying...',
    variant: 'warning',
  },
};

export const Error = {
  args: {
    open: true,
    message: 'Failed to delete device',
    variant: 'danger',
  },
};

export const LongMessage = {
  args: {
    open: true,
    message: 'The operation completed successfully but some warnings were encountered during processing',
    variant: 'warning',
  },
};

export const Closed = {
  args: {
    open: false,
    message: 'This notification is hidden',
    variant: 'info',
  },
};
