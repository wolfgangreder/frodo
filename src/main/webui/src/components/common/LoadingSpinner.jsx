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
import { Bullseye, Spinner } from '@patternfly/react-core';

/**
 * Loading spinner component with optional message
 */
function LoadingSpinner({ message = 'Loading...', fullPage = false }) {
  const content = (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1rem', padding: '2rem' }}>
      <Spinner aria-label={message} />
      {message && (
        <span style={{ color: 'var(--pf-v6-global--Color--200)' }}>{message}</span>
      )}
    </div>
  );

  if (fullPage) {
    return (
      <Bullseye style={{ minHeight: '50vh' }}>
        {content}
      </Bullseye>
    );
  }

  return content;
}

export default LoadingSpinner;
