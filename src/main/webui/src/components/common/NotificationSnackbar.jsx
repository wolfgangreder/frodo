import React from 'react';
import { Snackbar, Alert } from '@mui/material';
import { useUiStore } from '../../stores';

/**
 * Global notification snackbar component
 * Displays notifications from the UI store
 */
function NotificationSnackbar() {
  const { notification, clearNotification } = useUiStore();

  const handleClose = (event, reason) => {
    if (reason === 'clickaway') {
      return;
    }
    clearNotification();
  };

  if (!notification) {
    return null;
  }

  return (
    <Snackbar
      key={notification.key}
      open={Boolean(notification)}
      autoHideDuration={notification.duration}
      onClose={handleClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      sx={{
        // Ensure snackbar is above mobile bottom nav if added later
        mb: { xs: 7, sm: 0 },
      }}
    >
      <Alert
        onClose={handleClose}
        severity={notification.severity}
        variant="filled"
        sx={{ width: '100%' }}
      >
        {notification.message}
      </Alert>
    </Snackbar>
  );
}

export default NotificationSnackbar;
