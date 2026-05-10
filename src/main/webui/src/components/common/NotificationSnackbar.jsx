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

import React, { useState, useEffect } from 'react';
import { Snackbar, Alert } from '@mui/material';
import { useUiStore } from '../../stores';

/**
 * Global notification snackbar component with queue support.
 * Displays notifications from the UI store one at a time,
 * advancing to the next after the current one closes.
 */
function NotificationSnackbar() {
  const { notifications, dismissNotification } = useUiStore();
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState(null);

  // When a new notification arrives and none is showing, display it
  useEffect(() => {
    if (notifications.length > 0 && !current) {
      setCurrent(notifications[0]);
      setOpen(true);
    }
  }, [notifications, current]);

  const handleClose = (event, reason) => {
    if (reason === 'clickaway') {
      return;
    }
    setOpen(false);
  };

  const handleExited = () => {
    if (current) {
      dismissNotification(current.id);
    }
    setCurrent(null);
  };

  if (!current) {
    return null;
  }

  return (
    <Snackbar
      key={current.id}
      open={open}
      autoHideDuration={current.duration}
      onClose={handleClose}
      TransitionProps={{ onExited: handleExited }}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      sx={{
        mb: { xs: 7, sm: 0 },
      }}
    >
      <Alert
        onClose={handleClose}
        severity={current.severity}
        variant="filled"
        sx={{ width: '100%' }}
        role="alert"
      >
        {current.message}
      </Alert>
    </Snackbar>
  );
}

export default NotificationSnackbar;
