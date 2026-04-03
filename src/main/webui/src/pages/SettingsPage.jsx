import React from 'react';
import { Box, Card, CardContent, Typography, Divider } from '@mui/material';
import { PageHeader } from '../components/common';

/**
 * Settings page - application configuration
 * Placeholder implementation - will be expanded in Phase 6
 */
function SettingsPage() {
  return (
    <Box>
      <PageHeader
        title="Settings"
        subtitle="Configure application settings and preferences"
      />

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
            Metrics Collection
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Configure scraping intervals and data retention policies.
          </Typography>
          <Divider sx={{ my: 2 }} />

          <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
            Import / Export
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Backup and restore device configurations for disaster recovery.
          </Typography>
          <Divider sx={{ my: 2 }} />

          <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
            Grafana Integration
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Configure Grafana connection settings and panel preferences.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}

export default SettingsPage;
