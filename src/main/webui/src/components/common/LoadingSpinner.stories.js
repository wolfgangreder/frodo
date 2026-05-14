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

import LoadingSpinner from './LoadingSpinner';

export default {
  title: 'Common/LoadingSpinner',
  component: LoadingSpinner,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    message: {
      control: 'text',
      description: 'Loading message to display',
    },
    size: {
      control: { type: 'number', min: 20, max: 100, step: 10 },
      description: 'Spinner size in pixels',
    },
    fullPage: {
      control: 'boolean',
      description: 'Whether to display as full page loader',
    },
  },
};

export const Default = {
  args: {
    message: 'Loading...',
    size: 40,
    fullPage: false,
  },
};

export const WithCustomMessage = {
  args: {
    message: 'Fetching device data...',
    size: 40,
    fullPage: false,
  },
};

export const NoMessage = {
  args: {
    message: '',
    size: 40,
    fullPage: false,
  },
};

export const LargeSize = {
  args: {
    message: 'Loading...',
    size: 60,
    fullPage: false,
  },
};

export const SmallSize = {
  args: {
    message: 'Loading...',
    size: 24,
    fullPage: false,
  },
};

export const FullPage = {
  args: {
    message: 'Loading application...',
    size: 40,
    fullPage: true,
  },
  parameters: {
    layout: 'fullscreen',
  },
};
