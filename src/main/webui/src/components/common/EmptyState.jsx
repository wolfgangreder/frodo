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
  Card,
  CardBody,
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
  Button,
} from '@patternfly/react-core';
import { InboxIcon } from '@patternfly/react-icons';

/**
 * Reusable empty state component for lists and pages.
 *
 * @param {Object} props
 * @param {string} props.title - Heading text
 * @param {string} [props.description] - Description text
 * @param {React.ComponentType} [props.icon] - Custom icon component type (defaults to InboxIcon)
 * @param {string} [props.actionLabel] - Button label
 * @param {Function} [props.onAction] - Button click handler
 * @param {React.ReactNode} [props.children] - Optional custom content
 */
function EmptyStateComponent({
  title,
  description,
  icon,
  actionLabel,
  onAction,
  children,
}) {
  return (
    <Card>
      <CardBody>
        <EmptyState titleText={title} headingLevel="h2" icon={icon || InboxIcon}>
          {description && <EmptyStateBody>{description}</EmptyStateBody>}
          {(actionLabel && onAction) || children ? (
            <EmptyStateFooter>
              {actionLabel && onAction && (
                <EmptyStateActions>
                  <Button variant="secondary" onClick={onAction}>
                    {actionLabel}
                  </Button>
                </EmptyStateActions>
              )}
              {children && (
                <EmptyStateActions>{children}</EmptyStateActions>
              )}
            </EmptyStateFooter>
          ) : null}
        </EmptyState>
      </CardBody>
    </Card>
  );
}

export default EmptyStateComponent;
