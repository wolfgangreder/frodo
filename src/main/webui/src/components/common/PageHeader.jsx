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
import { Flex, FlexItem, Content } from '@patternfly/react-core';

/**
 * Page header component with title and optional actions
 */
function PageHeader({ title, subtitle, actions, style = {} }) {
  return (
    <Flex
      justifyContent={{ default: 'justifyContentSpaceBetween' }}
      alignItems={{ default: 'alignItemsCenter' }}
      style={{ marginBottom: '1.5rem', flexWrap: 'wrap', gap: '0.5rem', ...style }}
    >
      <FlexItem>
        <Content component="h1" style={{ fontWeight: 600, margin: 0 }}>
          {title}
        </Content>
        {subtitle && (
          <Content component="p" style={{ color: 'var(--pf-v6-global--Color--200)', marginTop: '0.25rem', marginBottom: 0 }}>
            {subtitle}
          </Content>
        )}
      </FlexItem>
      {actions && (
        <FlexItem>
          <Flex gap={{ default: 'gapSm' }} style={{ flexWrap: 'wrap' }}>
            {actions}
          </Flex>
        </FlexItem>
      )}
    </Flex>
  );
}

export default PageHeader;
