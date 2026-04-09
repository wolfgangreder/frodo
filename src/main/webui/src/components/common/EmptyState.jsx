import React from 'react';
import { Card, CardContent, Typography, Button, Box } from '@mui/material';
import InboxIcon from '@mui/icons-material/Inbox';

/**
 * Reusable empty state component for lists and pages
 *
 * @param {Object} props
 * @param {string} props.title - Heading text
 * @param {string} [props.description] - Description text
 * @param {React.ReactNode} [props.icon] - Custom icon (defaults to InboxIcon)
 * @param {string} [props.actionLabel] - Button label
 * @param {Function} [props.onAction] - Button click handler
 * @param {React.ReactNode} [props.children] - Optional custom content
 */
function EmptyState({
  title,
  description,
  icon,
  actionLabel,
  onAction,
  children,
}) {
  return (
    <Card>
      <CardContent
        sx={{
          textAlign: 'center',
          py: 6,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <Box sx={{ color: 'text.secondary', mb: 1 }}>
          {icon || <InboxIcon sx={{ fontSize: 48, opacity: 0.5 }} />}
        </Box>
        <Typography variant="h6" color="text.secondary" gutterBottom>
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {description}
          </Typography>
        )}
        {actionLabel && onAction && (
          <Button variant="outlined" color="primary" onClick={onAction}>
            {actionLabel}
          </Button>
        )}
        {children}
      </CardContent>
    </Card>
  );
}

export default EmptyState;
