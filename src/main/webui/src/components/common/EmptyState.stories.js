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
import EmptyState from './EmptyState';
import DevicesIcon from '@mui/icons-material/Devices';
import SearchIcon from '@mui/icons-material/Search';

export default {
  title: 'Common/EmptyState',
  component: EmptyState,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
  argTypes: {
    title: {
      control: 'text',
      description: 'Heading text',
    },
    description: {
      control: 'text',
      description: 'Description text',
    },
    actionLabel: {
      control: 'text',
      description: 'Button label',
    },
    onAction: {
      description: 'Button click handler',
    },
  },
  args: {
    onAction: fn(),
  },
};

export const Default = {
  args: {
    title: 'No items found',
    description: 'There are no items to display.',
  },
};

export const WithAction = {
  args: {
    title: 'No devices configured',
    description: 'Get started by adding your first device.',
    actionLabel: 'Add Device',
  },
};

export const WithCustomIcon = {
  args: {
    title: 'No devices found',
    description: 'Try adjusting your search or add a new device.',
    actionLabel: 'Add Device',
    icon: <DevicesIcon sx={{ fontSize: 48, opacity: 0.5 }} />,
  },
};

export const SearchResults = {
  args: {
    title: 'No results found',
    description: 'Try different search terms or clear your filters.',
    icon: <SearchIcon sx={{ fontSize: 48, opacity: 0.5 }} />,
  },
};

export const NoDescription = {
  args: {
    title: 'Empty list',
    actionLabel: 'Create New',
  },
};

export const LongDescription = {
  args: {
    title: 'No metrics data available',
    description: 'Metrics scraping is not configured for this device. Configure metrics collection to start gathering performance data and historical trends.',
    actionLabel: 'Configure Metrics',
  },
};
