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
  Alert,
  Card,
  CardBody,
  Label,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  PauseIcon,
  QuestionCircleIcon,
} from '@patternfly/react-icons';
import { formatForDisplay, formatTimeAgo } from '../../utils/timeZone';

const C = {
  primary:  'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:   'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text-color--disabled, #b8bbbe)',
  success:  'var(--pf-t--global--color--status--success--default, #3e8635)',
  danger:   'var(--pf-t--global--color--status--danger--default, #c9190b)',
};

/**
 * MetricsStatusCard - displays current scraping status
 *
 * @param {Object} props
 * @param {Object} props.status - Status data from API
 * @param {boolean} props.isLoading - Whether status is loading
 */
function MetricsStatusCard({ status, isLoading = false }) {
  if (isLoading || !status) return null;

  if (!status.configured) {
    return (
      <Alert
        variant="info"
        isInline
        title="Metrics scraping is not yet configured for this device. Configure parameters below to start collecting data."
        style={{ marginBottom: '1rem' }}
      />
    );
  }

  const statusIcon = () => {
    if (!status.enabled) return <PauseIcon style={{ color: C.disabled }} />;
    switch (status.lastScrapeStatus) {
      case 'SUCCESS':   return <CheckCircleIcon style={{ color: C.success }} />;
      case 'FAILED':
      case 'TIMEOUT':   return <ExclamationCircleIcon style={{ color: C.danger }} />;
      default:          return <QuestionCircleIcon style={{ color: C.disabled }} />;
    }
  };

  const statusColor = () => {
    if (!status.enabled) return 'grey';
    switch (status.lastScrapeStatus) {
      case 'SUCCESS':   return 'green';
      case 'FAILED':
      case 'TIMEOUT':   return 'red';
      default:          return 'grey';
    }
  };

  const statusLabel = () => {
    if (!status.enabled) return 'Paused';
    if (!status.lastScrapeStatus) return 'Waiting';
    return status.lastScrapeStatus;
  };

  return (
    <Card isCompact style={{ marginBottom: '1rem' }}>
      <CardBody>
        <Flex
          direction={{ default: 'column', sm: 'row' }}
          alignItems={{ sm: 'alignItemsCenter' }}
          gap={{ default: 'gapMd' }}
        >
          <FlexItem grow={{ default: 'grow' }}>
            <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
              <FlexItem>{statusIcon()}</FlexItem>
              <FlexItem>
                <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
                  <FlexItem>
                    <span style={{ fontWeight: 600, fontSize: '0.875rem' }}>Scraping Status</span>
                  </FlexItem>
                  <FlexItem>
                    <Label color={statusColor()} variant={status.enabled ? 'filled' : 'outline'}>
                      {statusLabel()}
                    </Label>
                  </FlexItem>
                </Flex>
                {status.lastScrapeTime && (
                  <div style={{ fontSize: '0.75rem', color: C.subtle, marginTop: '0.125rem' }}>
                    Last scrape: {formatForDisplay(status.lastScrapeTime)} ({formatTimeAgo(status.lastScrapeTime)})
                  </div>
                )}
              </FlexItem>
            </Flex>
          </FlexItem>

          <FlexItem>
            <Flex gap={{ default: 'gapLg' }}>
              <FlexItem style={{ textAlign: 'center' }}>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: C.primary }}>
                  {status.enabledParameterCount || 0}
                </div>
                <div style={{ fontSize: '0.75rem', color: C.subtle }}>Parameters</div>
              </FlexItem>
              {status.scrapeIntervalSeconds && (
                <FlexItem style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: '1.25rem', fontWeight: 700, color: C.primary }}>
                    {status.scrapeIntervalSeconds}s
                  </div>
                  <div style={{ fontSize: '0.75rem', color: C.subtle }}>Interval</div>
                </FlexItem>
              )}
            </Flex>
          </FlexItem>
        </Flex>

        {status.lastScrapeStatus === 'FAILED' && status.lastErrorMessage && (
          <Alert
            variant="danger"
            isInline
            title={status.lastErrorMessage}
            style={{ marginTop: '0.5rem' }}
          />
        )}
      </CardBody>
    </Card>
  );
}

export default MetricsStatusCard;
