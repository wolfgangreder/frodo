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
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Nav,
  NavList,
  NavItem,
  NavGroup,
  PageSidebar,
  PageSidebarBody,
} from '@patternfly/react-core';
import {
  HomeIcon,
  ServerIcon,
  FileAltIcon,
  ChartLineIcon,
  MicrochipIcon,
  SunIcon,
  CogIcon,
  InfoCircleIcon,
  EuroSignIcon,
} from '@patternfly/react-icons';

// Navigation items configuration
const navItems = [
  { title: 'Dashboard', path: '/', icon: <HomeIcon /> },
  { title: 'Devices', path: '/devices', icon: <ServerIcon /> },
  { title: 'Metrics Docs', path: '/metrics-docs', icon: <FileAltIcon /> },
  { title: 'Grafana', path: '/grafana', icon: <ChartLineIcon /> },
  { title: 'GPIO', path: '/gpio', icon: <MicrochipIcon /> },
  { title: 'Solar API', path: '/solar-api', icon: <SunIcon /> },
  { title: 'Cost Control', path: '/cost-control', icon: <EuroSignIcon /> },
  { title: 'Settings', path: '/settings', icon: <CogIcon /> },
];

const secondaryNavItems = [
  { title: 'About', path: '/about', icon: <InfoCircleIcon /> },
];

/**
 * Sidebar component — PatternFly Nav inside PageSidebar
 */
function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();

  const isSelected = (path) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(path);
  };

  const onNavSelect = (_event, result) => {
    navigate(result.itemId);
  };

  return (
    <PageSidebar>
      <PageSidebarBody>
        <Nav onSelect={onNavSelect} aria-label="Global navigation">
          <NavGroup title="Navigation">
            <NavList>
              {navItems.map((item) => (
                <NavItem
                  key={item.path}
                  itemId={item.path}
                  isActive={isSelected(item.path)}
                  onClick={() => navigate(item.path)}
                  icon={item.icon}
                >
                  {item.title}
                </NavItem>
              ))}
            </NavList>
          </NavGroup>
          <NavGroup title="">
            <NavList>
              {secondaryNavItems.map((item) => (
                <NavItem
                  key={item.path}
                  itemId={item.path}
                  isActive={isSelected(item.path)}
                  onClick={() => navigate(item.path)}
                  icon={item.icon}
                >
                  {item.title}
                </NavItem>
              ))}
            </NavList>
          </NavGroup>
        </Nav>
      </PageSidebarBody>
    </PageSidebar>
  );
}

export default Sidebar;
