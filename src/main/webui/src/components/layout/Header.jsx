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
  AppBar,
  Toolbar,
  IconButton,
  Typography,
  Box,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import { useUiStore } from '../../stores';

const DRAWER_WIDTH = 240;

/**
 * Header component with responsive behavior
 * - Shows hamburger menu on mobile (toggles mobile drawer)
 * - Shows hamburger menu on desktop (toggles sidebar collapse)
 */
function Header() {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { sidebarOpen, toggleSidebar, toggleMobileSidebar } = useUiStore();

  const handleMenuClick = () => {
    if (isMobile) {
      toggleMobileSidebar();
    } else {
      toggleSidebar();
    }
  };

  return (
    <AppBar
      position="fixed"
      sx={{
        width: {
          md: sidebarOpen ? `calc(100% - ${DRAWER_WIDTH}px)` : '100%',
        },
        ml: {
          md: sidebarOpen ? `${DRAWER_WIDTH}px` : 0,
        },
        transition: theme.transitions.create(['margin', 'width'], {
          easing: theme.transitions.easing.sharp,
          duration: theme.transitions.duration.leavingScreen,
        }),
      }}
    >
      <Toolbar>
        <IconButton
          color="inherit"
          aria-label="toggle sidebar"
          edge="start"
          onClick={handleMenuClick}
          sx={{ mr: 2 }}
        >
          <MenuIcon />
        </IconButton>

        <Typography
          variant="h6"
          noWrap
          component="div"
          sx={{
            flexGrow: 1,
            color: 'primary.main',
            fontWeight: 600,
          }}
        >
          Frodo
        </Typography>

        {/* Future: Add header actions here (notifications, user menu, etc.) */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {/* Placeholder for future header actions */}
        </Box>
      </Toolbar>
    </AppBar>
  );
}

export default Header;
