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

import React, { useState } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardBody,
  Modal,
  ModalBody,
  Button,
  Tooltip,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import {
  ExpandAltIcon,
  CompressAltIcon,
  ExternalLinkAltIcon,
} from '@patternfly/react-icons';
import GrafanaEmbed from './GrafanaEmbed';

/**
 * GrafanaPanel — PF Card wrapper around a single Grafana panel iframe.
 *
 * Props:
 *   title        {string}   Card header title
 *   src          {string}   Full Grafana panel URL
 *   externalUrl  {string}   URL to open the full dashboard in a new tab (optional)
 *   aspectRatio  {number}   Width/height ratio for the embed (default 16/9)
 *   minHeight    {number}   Minimum iframe height in px (default 220)
 */
function GrafanaPanel({ title, src, externalUrl, aspectRatio = 16 / 9 }) {
  const [fullscreen, setFullscreen] = useState(false);

  return (
    <>
      <Card style={{ height: '100%' }}>
        <CardHeader
          actions={{
            actions: (
              <Flex gap={{ default: 'gapXs' }}>
                {externalUrl && (
                  <FlexItem>
                    <Tooltip content="Open in Grafana">
                      <Button
                        variant="plain"
                        size="sm"
                        component="a"
                        href={externalUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label="Open panel in Grafana"
                      >
                        <ExternalLinkAltIcon />
                      </Button>
                    </Tooltip>
                  </FlexItem>
                )}
                <FlexItem>
                  <Tooltip content="Full screen">
                    <Button
                      variant="plain"
                      size="sm"
                      onClick={() => setFullscreen(true)}
                      aria-label="Enter full screen"
                    >
                      <ExpandAltIcon />
                    </Button>
                  </Tooltip>
                </FlexItem>
              </Flex>
            ),
          }}
        >
          <CardTitle>{title}</CardTitle>
        </CardHeader>
        <CardBody style={{ paddingTop: '0.5rem' }}>
          <GrafanaEmbed
            src={src}
            title={title}
            aspectRatio={aspectRatio}
            minHeight={220}
          />
        </CardBody>
      </Card>

      {/* Full-screen modal */}
      <Modal
        isOpen={fullscreen}
        onClose={() => setFullscreen(false)}
        variant="large"
        aria-labelledby="grafana-fullscreen-title"
        style={{ height: 'calc(100vh - 2rem)' }}
        hasNoBodyWrapper
      >
        <ModalBody style={{ padding: 0, display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header bar */}
          <Flex
            justifyContent={{ default: 'justifyContentSpaceBetween' }}
            alignItems={{ default: 'alignItemsCenter' }}
            style={{
              padding: '0.5rem 1rem',
              borderBottom: '1px solid var(--pf-t--global--border--color--default, #d2d2d2)',
              flexShrink: 0,
            }}
          >
            <FlexItem>
              <span id="grafana-fullscreen-title" style={{ fontWeight: 500 }}>{title}</span>
            </FlexItem>
            <FlexItem>
              <Tooltip content="Exit full screen">
                <Button
                  variant="plain"
                  size="sm"
                  onClick={() => setFullscreen(false)}
                  aria-label="Exit full screen"
                >
                  <CompressAltIcon />
                </Button>
              </Tooltip>
            </FlexItem>
          </Flex>

          {/* Full-size embed */}
          <div style={{ flex: 1, overflow: 'hidden' }}>
            <GrafanaEmbed
              src={src}
              title={title}
              aspectRatio={undefined}
              style={{ paddingTop: 0, height: '100%' }}
            />
          </div>
        </ModalBody>
      </Modal>
    </>
  );
}

export default GrafanaPanel;
