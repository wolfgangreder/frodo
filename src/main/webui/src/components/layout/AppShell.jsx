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
  Box,
  Link,
  Toolbar,
  useTheme,
} from '@mui/material';
import Header from './Header';
import Sidebar, { DRAWER_WIDTH } from './Sidebar';
import NotificationSnackbar from '../common/NotificationSnackbar';
import { useUiStore } from '../../stores';

/**
 * AppShell - Main layout wrapper with responsive sidebar
 * Renders Header, Sidebar, and main content area (via Outlet)
 */
function AppShell() {
  const theme = useTheme();
  const { sidebarOpen } = useUiStore();

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Skip to main content link for keyboard/screen reader users */}
      <Link
        href="#main-content"
        sx={{
          position: 'absolute',
          left: '-9999px',
          zIndex: 9999,
          padding: 2,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          textDecoration: 'none',
          fontWeight: 600,
          '&:focus': {
            left: theme.spacing(2),
            top: theme.spacing(2),
          },
        }}
      >
        Skip to main content
      </Link>

      <Header />
      <Sidebar />

      {/* Main content area */}
      <Box
        component="main"
        id="main-content"
        tabIndex={-1}
        sx={{
          flexGrow: 1,
          width: {
            md: sidebarOpen ? `calc(100% - ${DRAWER_WIDTH}px)` : '100%',
          },
          transition: theme.transitions.create(['margin', 'width'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.leavingScreen,
          }),
          backgroundColor: 'background.default',
          minHeight: '100vh',
          outline: 'none',
        }}
      >
        {/* Toolbar spacer to push content below AppBar */}
        <Toolbar />

        {/* Page content */}
        <Box
          sx={{
            p: { xs: 2, sm: 3 },
            maxWidth: '1600px',
          }}
        >
          <Outlet />
        </Box>
      </Box>

      {/* Global notification snackbar */}
      <NotificationSnackbar />
    </Box>
  );
}

export default AppShell;
