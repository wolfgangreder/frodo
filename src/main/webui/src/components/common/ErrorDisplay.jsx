import React from 'react';
import { Box, Typography, Button } from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

/**
 * Error display component with optional retry action
 */
function ErrorDisplay({ title = 'Error', message, onRetry, fullPage = false }) {
  const content = (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 2,
        p: 4,
        textAlign: 'center',
      }}
    >
      <ErrorOutlineIcon sx={{ fontSize: 48, color: 'error.main' }} />
      <Typography variant="h6" color="error">
        {title}
      </Typography>
      {message && (
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 400 }}>
          {message}
        </Typography>
      )}
      {onRetry && (
        <Button variant="outlined" color="primary" onClick={onRetry} sx={{ mt: 1 }}>
          Try Again
        </Button>
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

export default ErrorDisplay;
