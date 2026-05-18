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

import { fn } from '@storybook/test';
import ConfirmDialog from './ConfirmDialog';

export default {
  title: 'Common/ConfirmDialog',
  component: ConfirmDialog,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    open: {
      control: 'boolean',
      description: 'Whether dialog is open',
    },
    title: {
      control: 'text',
      description: 'Dialog title',
    },
    message: {
      control: 'text',
      description: 'Confirmation message',
    },
    confirmLabel: {
      control: 'text',
      description: 'Confirm button label',
    },
    cancelLabel: {
      control: 'text',
      description: 'Cancel button label',
    },
    confirmColor: {
      control: 'select',
      options: ['danger', 'warning', 'primary'],
      description: 'Confirm button variant',
    },
    isLoading: {
      control: 'boolean',
      description: 'Whether action is in progress',
    },
    showWarningIcon: {
      control: 'boolean',
      description: 'Show warning icon in title',
    },
  },
  args: {
    onClose: fn(),
    onConfirm: fn(),
  },
};

export const DeleteConfirmation = {
  args: {
    open: true,
    title: 'Delete Device',
    message: 'Are you sure you want to delete this device? This action cannot be undone.',
    confirmLabel: 'Delete',
    cancelLabel: 'Cancel',
    confirmColor: 'danger',
    isLoading: false,
    showWarningIcon: true,
  },
};

export const WarningAction = {
  args: {
    open: true,
    title: 'Disable Metrics',
    message: 'Disabling metrics will stop data collection. Historical data will be preserved.',
    confirmLabel: 'Disable',
    cancelLabel: 'Cancel',
    confirmColor: 'warning',
    isLoading: false,
    showWarningIcon: true,
  },
};

export const PrimaryAction = {
  args: {
    open: true,
    title: 'Apply Changes',
    message: 'Do you want to apply these configuration changes?',
    confirmLabel: 'Apply',
    cancelLabel: 'Cancel',
    confirmColor: 'primary',
    isLoading: false,
    showWarningIcon: false,
  },
};

export const Loading = {
  args: {
    open: true,
    title: 'Delete Device',
    message: 'Are you sure you want to delete this device? This action cannot be undone.',
    confirmLabel: 'Delete',
    cancelLabel: 'Cancel',
    confirmColor: 'danger',
    isLoading: true,
    showWarningIcon: true,
  },
};

export const NoIcon = {
  args: {
    open: true,
    title: 'Confirm Action',
    message: 'Please confirm this action.',
    confirmLabel: 'Confirm',
    cancelLabel: 'Cancel',
    confirmColor: 'primary',
    isLoading: false,
    showWarningIcon: false,
  },
};

export const LongMessage = {
  args: {
    open: true,
    title: 'Reset Configuration',
    message: 'This will reset all device configuration to factory defaults. All custom settings, metrics configuration, and schedules will be lost. This action cannot be undone. Are you sure you want to continue?',
    confirmLabel: 'Reset',
    cancelLabel: 'Cancel',
    confirmColor: 'danger',
    isLoading: false,
    showWarningIcon: true,
  },
};

export const Closed = {
  args: {
    open: false,
    title: 'Delete Device',
    message: 'Are you sure you want to delete this device?',
    confirmLabel: 'Delete',
    cancelLabel: 'Cancel',
    confirmColor: 'danger',
    isLoading: false,
    showWarningIcon: true,
  },
};
