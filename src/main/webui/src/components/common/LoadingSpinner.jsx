import React from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';

/**
 * Loading spinner component with optional message
 */
function LoadingSpinner({ message = 'Loading...', size = 40, fullPage = false }) {
  const content = (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 2,
        p: 4,
      }}
    >
      <CircularProgress size={size} color="primary" />
      {message && (
        <Typography variant="body2" color="text.secondary">
          {message}
        </Typography>
      )}
    </Box>
  );

  if (fullPage) {
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '50vh',
        }}
      >
        {content}
      </Box>
    );
  }

  return content;
}

export default LoadingSpinner;
