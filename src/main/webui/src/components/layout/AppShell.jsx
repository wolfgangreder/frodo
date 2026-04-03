import React from 'react';
import { Outlet } from 'react-router-dom';
import {
  Box,
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
      <Header />
      <Sidebar />

      {/* Main content area */}
      <Box
        component="main"
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
