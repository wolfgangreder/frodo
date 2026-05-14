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

import { Snackbar, Alert } from '@mui/material';

/**
 * Simplified NotificationSnackbar for Storybook demonstration.
 * The actual component uses Zustand store for queue management.
 */
function NotificationSnackbarDemo({ open, message, severity, onClose }) {
  return (
    <Snackbar
      open={open}
      autoHideDuration={6000}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
    >
      <Alert
        onClose={onClose}
        severity={severity}
        variant="filled"
        sx={{ width: '100%' }}
        role="alert"
      >
        {message}
      </Alert>
    </Snackbar>
  );
}

export default {
  title: 'Common/NotificationSnackbar',
  component: NotificationSnackbarDemo,
  parameters: {
    layout: 'fullscreen',
  },
  tags: ['autodocs'],
  argTypes: {
    open: {
      control: 'boolean',
      description: 'Whether snackbar is visible',
    },
    message: {
      control: 'text',
      description: 'Notification message',
    },
    severity: {
      control: 'select',
      options: ['success', 'info', 'warning', 'error'],
      description: 'Notification severity',
    },
  },
};

export const Success = {
  args: {
    open: true,
    message: 'Device saved successfully',
    severity: 'success',
  },
};

export const Info = {
  args: {
    open: true,
    message: 'Metrics scraping started',
    severity: 'info',
  },
};

export const Warning = {
  args: {
    open: true,
    message: 'Connection timeout - retrying...',
    severity: 'warning',
  },
};

export const Error = {
  args: {
    open: true,
    message: 'Failed to delete device',
    severity: 'error',
  },
};

export const LongMessage = {
  args: {
    open: true,
    message: 'The operation completed successfully but some warnings were encountered during processing',
    severity: 'warning',
  },
};

export const Closed = {
  args: {
    open: false,
    message: 'This notification is hidden',
    severity: 'info',
  },
};
