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
import { Button, CircularProgress } from '@mui/material';
import WifiTetheringIcon from '@mui/icons-material/WifiTethering';

/**
 * Connection test button component
 * 
 * @param {Object} props
 * @param {Function} props.onTest - Callback when test is clicked
 * @param {boolean} props.isTesting - Whether test is in progress
 * @param {boolean} props.disabled - Whether button is disabled
 */
function ConnectionTestButton({ onTest, isTesting = false, disabled = false }) {
  return (
    <Button
      variant="outlined"
      color="secondary"
      onClick={onTest}
      disabled={disabled || isTesting}
      startIcon={
        isTesting ? (
          <CircularProgress size={20} color="inherit" />
        ) : (
          <WifiTetheringIcon />
        )
      }
    >
      {isTesting ? 'Testing...' : 'Test Connection'}
    </Button>
  );
}

export default ConnectionTestButton;
