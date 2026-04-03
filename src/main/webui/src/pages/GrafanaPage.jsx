import React from 'react';
import { Box, Card, CardContent, Typography } from '@mui/material';
import { PageHeader } from '../components/common';

/**
 * Grafana page - embedded Grafana dashboards
 * Placeholder implementation - will be expanded in Phase 5
 */
function GrafanaPage() {
  return (
    <Box>
      <PageHeader
        title="Grafana Dashboards"
        subtitle="View detailed metrics and visualizations"
      />

      <Card>
        <CardContent sx={{ textAlign: 'center', py: 6 }}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            Grafana Integration Coming Soon
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Embedded Grafana panels will be displayed here for detailed metrics visualization.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Make sure Grafana is running at{' '}
            <Typography component="span" sx={{ color: 'secondary.main' }}>
              localhost:3000
            </Typography>
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}

export default GrafanaPage;
