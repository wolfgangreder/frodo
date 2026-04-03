import React from 'react';
import { Button, CircularProgress } from '@mui/material';
import WifiTetheringIcon from '@mui/icons-material/WifiTethering';

/**
 * Connection test button component
 * 
 * @param {Object} props
 * @param {Function} props.onTest - Callback when test is clicked
 * @param {boolean} props.isTesting - Whether test is in progress
 * @param {boolean} props.disabled - Whether button is disabled
 */
function ConnectionTestButton({ onTest, isTesting = false, disabled = false }) {
  return (
    <Button
      variant="outlined"
      color="secondary"
      onClick={onTest}
      disabled={disabled || isTesting}
      startIcon={
        isTesting ? (
          <CircularProgress size={20} color="inherit" />
        ) : (
          <WifiTetheringIcon />
        )
      }
    >
      {isTesting ? 'Testing...' : 'Test Connection'}
    </Button>
  );
}

export default ConnectionTestButton;
