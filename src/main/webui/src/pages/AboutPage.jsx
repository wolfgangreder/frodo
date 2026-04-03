import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Link,
  Divider,
  Grid,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, LoadingSpinner } from '../components/common';
import { systemApi } from '../services';

/**
 * About page - application information and resources
 */
function AboutPage() {
  const { data: appInfo, isLoading } = useQuery({
    queryKey: ['appInfo'],
    queryFn: systemApi.getInfo,
  });

  return (
    <Box>
      <PageHeader
        title="About"
        subtitle="Information about Frodo PV Monitoring System"
      />

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
                Application
              </Typography>
              {isLoading ? (
                <LoadingSpinner message="" size={24} />
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Name</Typography>
                    <Typography variant="body1">{appInfo?.name || 'Frodo'}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Version</Typography>
                    <Typography variant="body1">{appInfo?.version || '0.0.0'}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Description</Typography>
                    <Typography variant="body1">
                      {appInfo?.description || 'Modbus protocol connector for PV devices'}
                    </Typography>
                  </Box>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
                Technology Stack
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Backend</Typography>
                  <Typography variant="body2">Quarkus 3.x, Java 21</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Frontend</Typography>
                  <Typography variant="body2">React 19, MUI 6</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Protocol</Typography>
                  <Typography variant="body2">Modbus TCP, SunSpec</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Database</Typography>
                  <Typography variant="body2">FirebirdSQL</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
                Resources
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Link
                  href="/swagger-ui"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main' }}
                >
                  Swagger UI - Interactive API Documentation
                </Link>
                <Link
                  href="/q/openapi"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main' }}
                >
                  OpenAPI Specification (JSON)
                </Link>
                <Divider sx={{ my: 1 }} />
                <Link
                  href="https://sunspec.org/sunspec-modbus-specifications/"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main' }}
                >
                  SunSpec Modbus Specifications
                </Link>
                <Link
                  href="https://quarkus.io/guides/"
                  target="_blank"
                  rel="noreferrer"
                  sx={{ color: 'secondary.main' }}
                >
                  Quarkus Documentation
                </Link>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}

export default AboutPage;
