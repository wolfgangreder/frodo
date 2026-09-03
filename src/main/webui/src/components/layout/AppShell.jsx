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
import { Outlet } from 'react-router-dom';
import {
  Page,
  PageSection,
  SkipToContent,
} from '@patternfly/react-core';
import Header from './Header';
import Sidebar from './Sidebar';
import NotificationSnackbar from '../common/NotificationSnackbar';

/**
 * AppShell — PatternFly Page layout with Masthead, Sidebar, and main content
 */
function AppShell() {
  const skipToContent = (
    <SkipToContent href="#main-content">Skip to main content</SkipToContent>
  );

  return (
    <Page
      isManagedSidebar
      masthead={<Header />}
      sidebar={<Sidebar />}
      skipToContent={skipToContent}
      mainContainerId="main-content"
    >
      <PageSection>
        <Outlet />
      </PageSection>

      {/* Global notification toasts */}
      <NotificationSnackbar />
    </Page>
  );
}

export default AppShell;
