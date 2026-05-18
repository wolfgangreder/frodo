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
import { Button } from '@patternfly/react-core';
import { ExclamationCircleIcon, HomeIcon } from '@patternfly/react-icons';
import { useNavigate } from 'react-router-dom';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:  'var(--pf-t--global--text-color--subtle, #6a6e73)',
};

/**
 * 404 Not Found page
 */
function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        textAlign: 'center',
        padding: '1.5rem',
      }}
    >
      <ExclamationCircleIcon style={{ fontSize: 80, width: 80, height: 80, color: C.subtle, marginBottom: '1rem' }} />
      <h2 style={{ fontWeight: 700, color: C.primary, margin: '0 0 0.25rem' }}>404</h2>
      <h5 style={{ color: C.subtle, margin: '0 0 0.25rem', fontWeight: 400, fontSize: '1.25rem' }}>
        Page Not Found
      </h5>
      <p style={{ color: C.subtle, maxWidth: 400, marginBottom: '2rem' }}>
        The page you&apos;re looking for doesn&apos;t exist or has been moved.
      </p>
      <Button
        variant="primary"
        icon={<HomeIcon />}
        onClick={() => navigate('/')}
      >
        Back to Dashboard
      </Button>
    </div>
  );
}

export default NotFoundPage;
