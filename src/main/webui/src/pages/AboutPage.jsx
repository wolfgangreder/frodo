/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {
  Card,
  CardBody,
  Divider,
  Grid,
  GridItem,
} from '@patternfly/react-core';
import { useQuery } from '@tanstack/react-query';
import { PageHeader, LoadingSpinner } from '../components/common';
import { systemApi } from '../services';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:  'var(--pf-t--global--text-color--subtle, #6a6e73)',
  link:    'var(--pf-t--global--color--brand--default, #0066cc)',
};

function InfoRow({ label, value }) {
  return (
    <div style={{ marginBottom: '0.5rem' }}>
      <div style={{ fontSize: '0.75rem', color: C.subtle }}>{label}</div>
      <div style={{ fontSize: '0.875rem' }}>{value}</div>
    </div>
  );
}

/**
 * About page - application information and resources
 */
function AboutPage() {
  const { data: appInfo, isLoading } = useQuery({
    queryKey: ['appInfo'],
    queryFn: systemApi.getInfo,
  });

  return (
    <div>
      <PageHeader
        title="About"
        subtitle="Information about Frodo PV Monitoring System"
      />

      <Grid hasGutter>
        <GridItem span={12} md={6}>
          <Card>
            <CardBody>
              <h3 style={{ color: C.primary, marginTop: 0, marginBottom: '1rem', fontSize: '1rem' }}>
                Application
              </h3>
              {isLoading ? (
                <LoadingSpinner message="" size={24} />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  <InfoRow label="Name" value={appInfo?.name || 'Frodo'} />
                  <InfoRow label="Version" value={appInfo?.version || '0.0.0'} />
                  <InfoRow label="Description" value={appInfo?.description || 'Modbus protocol connector for PV devices'} />
                </div>
              )}
            </CardBody>
          </Card>
        </GridItem>

        <GridItem span={12} md={6}>
          <Card>
            <CardBody>
              <h3 style={{ color: C.primary, marginTop: 0, marginBottom: '1rem', fontSize: '1rem' }}>
                Technology Stack
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {[
                  { label: 'Backend',  value: 'Quarkus 3.x, Java 25' },
                  { label: 'Frontend', value: 'React 19, PatternFly 6' },
                  { label: 'Protocol', value: 'Modbus TCP, SunSpec' },
                  { label: 'Database', value: 'FirebirdSQL' },
                ].map(({ label, value }) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '0.875rem', color: C.subtle }}>{label}</span>
                    <span style={{ fontSize: '0.875rem' }}>{value}</span>
                  </div>
                ))}
              </div>
            </CardBody>
          </Card>
        </GridItem>

        <GridItem span={12}>
          <Card>
            <CardBody>
              <h3 style={{ color: C.primary, marginTop: 0, marginBottom: '1rem', fontSize: '1rem' }}>
                Resources
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <a href="/swagger-ui" target="_blank" rel="noreferrer" style={{ color: C.link }}>
                  Swagger UI - Interactive API Documentation
                </a>
                <Divider style={{ margin: '0.5rem 0' }} />
                <a
                  href="https://quarkus.io/guides/"
                  target="_blank"
                  rel="noreferrer"
                  style={{ color: C.link }}
                >
                  Quarkus Documentation
                </a>
              </div>
            </CardBody>
          </Card>
        </GridItem>
      </Grid>
    </div>
  );
}

export default AboutPage;
