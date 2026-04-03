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
