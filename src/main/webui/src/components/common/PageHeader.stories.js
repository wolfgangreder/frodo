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
import PageHeader from './PageHeader';
import { Button, IconButton } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RefreshIcon from '@mui/icons-material/Refresh';
import SettingsIcon from '@mui/icons-material/Settings';

export default {
  title: 'Common/PageHeader',
  component: PageHeader,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
  argTypes: {
    title: {
      control: 'text',
      description: 'Page title',
    },
    subtitle: {
      control: 'text',
      description: 'Optional subtitle',
    },
  },
};

export const Default = {
  args: {
    title: 'Devices',
  },
};

export const WithSubtitle = {
  args: {
    title: 'Devices',
    subtitle: 'Manage your Modbus devices and connections',
  },
};

export const WithSingleAction = {
  args: {
    title: 'Devices',
    subtitle: 'Manage your Modbus devices',
    actions: (
      <Button variant="contained" startIcon={<AddIcon />} onClick={fn()}>
        Add Device
      </Button>
    ),
  },
};

export const WithMultipleActions = {
  args: {
    title: 'Metrics',
    subtitle: 'View and analyze device metrics',
    actions: (
      <>
        <IconButton onClick={fn()} aria-label="refresh">
          <RefreshIcon />
        </IconButton>
        <IconButton onClick={fn()} aria-label="settings">
          <SettingsIcon />
        </IconButton>
        <Button variant="outlined" onClick={fn()}>
          Export
        </Button>
        <Button variant="contained" startIcon={<AddIcon />} onClick={fn()}>
          Configure
        </Button>
      </>
    ),
  },
};

export const LongTitle = {
  args: {
    title: 'Cost Control Configuration',
    subtitle: 'Configure energy pricing, tariff windows, grid fees, and fixed costs',
    actions: (
      <Button variant="contained" onClick={fn()}>
        Save Changes
      </Button>
    ),
  },
};

export const NoSubtitle = {
  args: {
    title: 'Dashboard',
    actions: (
      <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fn()}>
        Refresh
      </Button>
    ),
  },
};
