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
import { AlertGroup, Alert, AlertActionCloseButton } from '@patternfly/react-core';
import { useUiStore } from '../../stores';

/**
 * Global notification toast component with queue support.
 * Displays notifications from the UI store one at a time,
 * advancing to the next after the current one closes.
 */
function NotificationSnackbar() {
  const { notifications, dismissNotification } = useUiStore();
  const current = notifications[0] || null;

  const handleClose = () => {
    if (current) {
      dismissNotification(current.id);
    }
  };

  if (!current) {
    return null;
  }

  // MUI uses 'error'; PF uses 'danger'
  const variant = current.severity === 'error' ? 'danger' : current.severity;

  return (
    <AlertGroup isToast isLiveRegion>
      <Alert
        key={current.id}
        variant={variant}
        title={current.message}
        timeout={current.duration}
        onTimeout={handleClose}
        actionClose={
          <AlertActionCloseButton
            title={`Close ${variant} alert`}
            variantLabel={variant}
            onClose={handleClose}
          />
        }
      />
    </AlertGroup>
  );
}

export default NotificationSnackbar;
