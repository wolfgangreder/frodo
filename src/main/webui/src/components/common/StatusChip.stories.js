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

import StatusChip from './StatusChip';

export default {
  title: 'Common/StatusChip',
  component: StatusChip,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    status: {
      control: 'select',
      options: ['CONNECTED', 'DISCONNECTED', 'UNKNOWN', 'CONNECTING'],
      description: 'Connection status',
    },
    size: {
      control: 'radio',
      options: ['sm', 'md'],
      description: 'Label size',
    },
    variant: {
      control: 'radio',
      options: ['outline', 'filled'],
      description: 'Label variant',
    },
  },
};

export const Connected = {
  args: {
    status: 'CONNECTED',
    size: 'sm',
    variant: 'outline',
  },
};

export const Disconnected = {
  args: {
    status: 'DISCONNECTED',
    size: 'sm',
    variant: 'outline',
  },
};

export const Unknown = {
  args: {
    status: 'UNKNOWN',
    size: 'sm',
    variant: 'outline',
  },
};

export const Connecting = {
  args: {
    status: 'CONNECTING',
    size: 'sm',
    variant: 'outline',
  },
};

export const MediumSize = {
  args: {
    status: 'CONNECTED',
    size: 'md',
    variant: 'outline',
  },
};

export const FilledVariant = {
  args: {
    status: 'CONNECTED',
    size: 'sm',
    variant: 'filled',
  },
};
