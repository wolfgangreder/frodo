import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Box,
  Typography,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import DevicesIcon from '@mui/icons-material/Devices';
import InsightsIcon from '@mui/icons-material/Insights';
import SettingsIcon from '@mui/icons-material/Settings';
import InfoIcon from '@mui/icons-material/Info';
import { useUiStore } from '../../stores';

const DRAWER_WIDTH = 240;

// Navigation items configuration
const navItems = [
  {
    title: 'Dashboard',
    path: '/',
    icon: <DashboardIcon />,
  },
  {
    title: 'Devices',
    path: '/devices',
    icon: <DevicesIcon />,
  },
  {
    title: 'Grafana',
    path: '/grafana',
    icon: <InsightsIcon />,
  },
  {
    title: 'Settings',
    path: '/settings',
    icon: <SettingsIcon />,
  },
];

const secondaryNavItems = [
  {
    title: 'About',
    path: '/about',
    icon: <InfoIcon />,
  },
];

/**
 * Sidebar component with responsive behavior
 * - Permanent drawer on desktop (collapsible)
 * - Temporary drawer on mobile (overlay)
 */
function Sidebar() {
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const {
    sidebarOpen,
    sidebarMobileOpen,
    setMobileSidebarOpen,
  } = useUiStore();

  const handleNavigation = (path) => {
    navigate(path);
    if (isMobile) {
      setMobileSidebarOpen(false);
    }
  };

  const isSelected = (path) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(path);
  };

  const drawerContent = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Logo / Brand */}
      <Box
        sx={{
          p: 2,
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Typography
          variant="h5"
          sx={{
            color: 'primary.main',
            fontWeight: 700,
          }}
        >
          Frodo
        </Typography>
        <Typography
          variant="caption"
          sx={{
            color: 'text.secondary',
            ml: 'auto',
          }}
        >
          PV Monitor
        </Typography>
      </Box>

      {/* Main Navigation */}
      <List sx={{ flexGrow: 1, pt: 1 }}>
        {navItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <ListItemButton
              selected={isSelected(item.path)}
              onClick={() => handleNavigation(item.path)}
              sx={{ mx: 1, borderRadius: 1 }}
            >
              <ListItemIcon
                sx={{
                  color: isSelected(item.path) ? 'primary.main' : 'text.secondary',
                  minWidth: 40,
                }}
              >
                {item.icon}
              </ListItemIcon>
              <ListItemText
                primary={item.title}
                primaryTypographyProps={{
                  fontWeight: isSelected(item.path) ? 600 : 400,
                }}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>

      <Divider />

      {/* Secondary Navigation */}
      <List sx={{ pt: 1, pb: 2 }}>
        {secondaryNavItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <ListItemButton
              selected={isSelected(item.path)}
              onClick={() => handleNavigation(item.path)}
              sx={{ mx: 1, borderRadius: 1 }}
            >
              <ListItemIcon
                sx={{
                  color: isSelected(item.path) ? 'primary.main' : 'text.secondary',
                  minWidth: 40,
                }}
              >
                {item.icon}
              </ListItemIcon>
              <ListItemText
                primary={item.title}
                primaryTypographyProps={{
                  fontWeight: isSelected(item.path) ? 600 : 400,
                }}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  );

  return (
    <Box
      component="nav"
      sx={{
        width: { md: sidebarOpen ? DRAWER_WIDTH : 0 },
        flexShrink: { md: 0 },
      }}
    >
      {/* Mobile drawer (temporary) */}
      <Drawer
        variant="temporary"
        open={sidebarMobileOpen}
        onClose={() => setMobileSidebarOpen(false)}
        ModalProps={{
          keepMounted: true, // Better mobile performance
        }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: DRAWER_WIDTH,
          },
        }}
      >
        {drawerContent}
      </Drawer>

      {/* Desktop drawer (permanent) */}
      <Drawer
        variant="persistent"
        open={sidebarOpen}
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: DRAWER_WIDTH,
          },
        }}
      >
        {drawerContent}
      </Drawer>
    </Box>
  );
}

export default Sidebar;
export { DRAWER_WIDTH };
