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
  Masthead,
  MastheadMain,
  MastheadToggle,
  MastheadBrand,
  MastheadContent,
  Button,
} from '@patternfly/react-core';
import { BarsIcon } from '@patternfly/react-icons';
import { useUiStore } from '../../stores';

/**
 * Header component — PatternFly Masthead with sidebar toggle
 */
function Header() {
  const { toggleSidebar } = useUiStore();

  return (
    <Masthead>
      <MastheadToggle>
        <Button
          variant="plain"
          onClick={toggleSidebar}
          aria-label="Toggle navigation"
        >
          <BarsIcon />
        </Button>
      </MastheadToggle>
      <MastheadMain>
        <MastheadBrand>Frodo</MastheadBrand>
      </MastheadMain>
      <MastheadContent>
        {/* Future: header actions (notifications, user menu, etc.) */}
      </MastheadContent>
    </Masthead>
  );
}

export default Header;
