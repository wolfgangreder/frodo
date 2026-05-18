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
  FormGroup,
  TextInput,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Flex,
  FlexItem,
} from '@patternfly/react-core';

const INTERVAL_MARKS = [
  { value: 1,   label: '1s' },
  { value: 5,   label: '5s' },
  { value: 15,  label: '15s' },
  { value: 30,  label: '30s' },
  { value: 60,  label: '1m' },
  { value: 120, label: '2m' },
  { value: 300, label: '5m' },
];

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:  'var(--pf-t--global--text-color--subtle, #6a6e73)',
};

function formatLabel(val) {
  if (val >= 60) {
    const mins = Math.floor(val / 60);
    const secs = val % 60;
    return secs > 0 ? `${mins}m ${secs}s` : `${mins}m`;
  }
  return `${val}s`;
}

/**
 * ScrapingIntervalInput - range slider + number input for scrape interval
 *
 * @param {Object} props
 * @param {number} props.value - Current interval in seconds
 * @param {Function} props.onChange - Callback with new value
 * @param {boolean} props.disabled - Whether the input is disabled
 */
function ScrapingIntervalInput({ value = 30, onChange, disabled = false }) {
  const handleSliderChange = (e) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val)) onChange(val);
  };

  const handleInputChange = (_event, val) => {
    const num = parseInt(val, 10);
    if (!isNaN(num) && num >= 1 && num <= 300) onChange(num);
  };

  // Build tick mark datalist
  const datalistId = 'scraping-interval-ticks';

  return (
    <div>
      <div style={{ fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
        Scrape Interval
      </div>
      <div style={{ fontSize: '0.75rem', color: C.subtle, marginBottom: '0.75rem' }}>
        How often to read SunSpec data from the device ({formatLabel(value)})
      </div>

      <Flex gap={{ default: 'gapMd' }} alignItems={{ default: 'alignItemsCenter' }}>
        <FlexItem grow={{ default: 'grow' }}>
          <input
            type="range"
            min={1}
            max={300}
            step={1}
            value={value}
            onChange={handleSliderChange}
            disabled={disabled}
            list={datalistId}
            style={{
              width: '100%',
              accentColor: C.primary,
              cursor: disabled ? 'not-allowed' : 'pointer',
            }}
            aria-label="Scraping interval slider"
          />
          {/* Tick marks */}
          <datalist id={datalistId}>
            {INTERVAL_MARKS.map((m) => (
              <option key={m.value} value={m.value} label={m.label} />
            ))}
          </datalist>
          {/* Visible labels row */}
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', color: C.subtle, marginTop: '0.125rem' }}>
            {INTERVAL_MARKS.map((m) => (
              <span key={m.value}>{m.label}</span>
            ))}
          </div>
        </FlexItem>

        <FlexItem>
          <FormGroup fieldId="scraping-interval-input">
            <TextInput
              id="scraping-interval-input"
              type="number"
              value={String(value)}
              onChange={handleInputChange}
              isDisabled={disabled}
              min={1}
              max={300}
              step={1}
              style={{ width: 90 }}
              aria-label="Scraping interval in seconds"
            />
            <FormHelperText>
              <HelperText>
                <HelperTextItem>seconds</HelperTextItem>
              </HelperText>
            </FormHelperText>
          </FormGroup>
        </FlexItem>
      </Flex>
    </div>
  );
}

export default ScrapingIntervalInput;
