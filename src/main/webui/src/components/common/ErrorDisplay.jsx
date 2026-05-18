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
import {
  Bullseye,
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
  Button,
} from '@patternfly/react-core';
import { ExclamationCircleIcon } from '@patternfly/react-icons';

/**
 * Error display component with optional retry action
 */
function ErrorDisplay({ title = 'Error', message, onRetry, fullPage = false }) {
  const content = (
    <EmptyState
      titleText={title}
      headingLevel="h2"
      icon={ExclamationCircleIcon}
      status="danger"
    >
      {message && <EmptyStateBody>{message}</EmptyStateBody>}
      {onRetry && (
        <EmptyStateFooter>
          <EmptyStateActions>
            <Button variant="secondary" onClick={onRetry}>
              Try Again
            </Button>
          </EmptyStateActions>
        </EmptyStateFooter>
      )}
    </EmptyState>
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

export default ErrorDisplay;
