import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  Chip,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import { systemApi } from '../services';

/**
 * Dashboard page - main overview of PV system status
 */
function DashboardPage() {
  const {
    data: appInfo,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['appInfo'],
    queryFn: systemApi.getInfo,
  });

  if (isLoading) {
    return <LoadingSpinner message="Loading dashboard..." fullPage />;
  }

  if (error) {
    return (
      <ErrorDisplay
        title="Failed to load dashboard"
        message={error.message}
        onRetry={refetch}
        fullPage
      />
    );
  }

  return (
    <Box>
      <PageHeader
        title="Dashboard"
        subtitle="Overview of your PV monitoring system"
      />

      <Grid container spacing={3}>
        {/* Application Info Card */}
        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
                Application Info
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Name</Typography>
                  <Typography variant="body2">{appInfo?.name || 'Frodo'}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Version</Typography>
                  <Chip label={appInfo?.version || '0.0.0'} size="small" color="primary" />
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Description</Typography>
                  <Typography variant="body2" sx={{ textAlign: 'right', maxWidth: '60%' }}>
                    {appInfo?.description || 'PV Monitoring System'}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Quick Links Card */}
        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
                Quick Links
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Typography
                  component="a"
                  href="/swagger-ui"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                >
                  Swagger UI (REST API)
                </Typography>
                <Typography
                  component="a"
                  href="/q/metrics"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                >
                  Prometheus Metrics
                </Typography>
                <Typography
                  component="a"
                  href="/q/health"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                >
                  Health Check
                </Typography>
                <Typography
                  component="a"
                  href="/q/openapi"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
                >
                  OpenAPI Spec
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Placeholder for future widgets */}
        <Grid size={{ xs: 12, md: 6, lg: 4 }}>
          <Card sx={{ height: '100%', minHeight: 200 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
                System Status
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Device status widgets will appear here once devices are configured.
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}

export default DashboardPage;
