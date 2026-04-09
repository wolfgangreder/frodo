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
