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
import ErrorDisplay from './ErrorDisplay';

export default {
  title: 'Common/ErrorDisplay',
  component: ErrorDisplay,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    title: {
      control: 'text',
      description: 'Error title',
    },
    message: {
      control: 'text',
      description: 'Error message',
    },
    onRetry: {
      description: 'Retry callback function',
    },
    fullPage: {
      control: 'boolean',
      description: 'Whether to display as full page error',
    },
  },
  args: {
    onRetry: fn(),
  },
};

export const Default = {
  args: {
    title: 'Error',
    message: 'Something went wrong. Please try again.',
    fullPage: false,
  },
};

export const WithRetry = {
  args: {
    title: 'Connection Failed',
    message: 'Unable to connect to the server. Please check your connection and try again.',
    fullPage: false,
  },
};

export const NoRetryButton = {
  args: {
    title: 'Access Denied',
    message: 'You do not have permission to access this resource.',
    onRetry: undefined,
    fullPage: false,
  },
};

export const CustomTitle = {
  args: {
    title: 'Device Not Found',
    message: 'The requested device could not be found in the system.',
    fullPage: false,
  },
};

export const LongMessage = {
  args: {
    title: 'Operation Failed',
    message: 'The operation could not be completed due to a network timeout. This may be caused by slow network conditions or server overload. Please wait a moment and try again.',
    fullPage: false,
  },
};

export const FullPage = {
  args: {
    title: 'Page Load Error',
    message: 'Failed to load the requested page. Please refresh and try again.',
    fullPage: true,
  },
  parameters: {
    layout: 'fullscreen',
  },
};
