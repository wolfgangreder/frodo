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

import React, { useState, useRef } from 'react';
import { Skeleton } from '@patternfly/react-core';

/**
 * GrafanaEmbed — renders a single Grafana panel as an iframe.
 *
 * Props:
 *   src          {string}  Full Grafana panel URL (built by grafanaService)
 *   title        {string}  Accessible title for the iframe
 *   aspectRatio  {number}  Width/height ratio (default 16/9)
 *   minHeight    {number}  Minimum height in px (default 200)
 *   style        {object}  Extra inline styles for the container div
 */
function GrafanaEmbed({ src, title = 'Grafana panel', aspectRatio = 16 / 9, minHeight = 200, style }) {
  const [loaded, setLoaded] = useState(false);
  const [error, setError]   = useState(false);
  const iframeRef = useRef(null);

  const handleLoad  = () => setLoaded(true);
  const handleError = () => { setLoaded(true); setError(true); };

  const paddingTop = aspectRatio ? `${(1 / aspectRatio) * 100}%` : undefined;

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        paddingTop: paddingTop || 0,
        minHeight: paddingTop ? undefined : '100%',
        height: paddingTop ? undefined : '100%',
        overflow: 'hidden',
        borderRadius: '4px',
        background: 'var(--pf-t--global--background--color--primary--default, #1b1d21)',
        ...style,
      }}
    >
      {/* Loading skeleton shown until iframe fires load */}
      {!loaded && (
        <Skeleton
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
          }}
        />
      )}

      {error ? (
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--pf-t--global--text-color--subtle, #6a6e73)',
            fontSize: '0.875rem',
          }}
        >
          Failed to load Grafana panel
        </div>
      ) : (
        <iframe
          ref={iframeRef}
          src={src}
          title={title}
          onLoad={handleLoad}
          onError={handleError}
          allowFullScreen
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            border: 'none',
            opacity: loaded ? 1 : 0,
            transition: 'opacity 0.3s',
          }}
        />
      )}
    </div>
  );
}

export default GrafanaEmbed;
