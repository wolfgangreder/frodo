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
import { fn } from '@storybook/test';
import PageHeader from './PageHeader';
import { Button } from '@patternfly/react-core';
import { PlusIcon, SyncAltIcon, CogIcon } from '@patternfly/react-icons';

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
      <Button variant="primary" icon={<PlusIcon />} onClick={fn()}>
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
        <Button variant="plain" onClick={fn()} aria-label="refresh">
          <SyncAltIcon />
        </Button>
        <Button variant="plain" onClick={fn()} aria-label="settings">
          <CogIcon />
        </Button>
        <Button variant="secondary" onClick={fn()}>
          Export
        </Button>
        <Button variant="primary" icon={<PlusIcon />} onClick={fn()}>
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
      <Button variant="primary" onClick={fn()}>
        Save Changes
      </Button>
    ),
  },
};

export const NoSubtitle = {
  args: {
    title: 'Dashboard',
    actions: (
      <Button variant="secondary" icon={<SyncAltIcon />} onClick={fn()}>
        Refresh
      </Button>
    ),
  },
};
