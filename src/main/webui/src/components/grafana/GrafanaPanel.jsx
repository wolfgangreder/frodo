import React, { useState } from 'react';
import {
  Card,
  CardContent,
  CardHeader,
  IconButton,
  Dialog,
  DialogContent,
  Tooltip,
  Box,
} from '@mui/material';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import FullscreenExitIcon from '@mui/icons-material/FullscreenExit';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import GrafanaEmbed from './GrafanaEmbed';

/**
 * GrafanaPanel — MUI Card wrapper around a single Grafana panel iframe.
 *
 * Props:
 *   title        {string}   Card header title
 *   src          {string}   Full Grafana panel URL
 *   externalUrl  {string}   URL to open the full dashboard in a new tab (optional)
 *   aspectRatio  {number}   Width/height ratio for the embed (default 16/9)
 *   minHeight    {number}   Minimum iframe height in px (default 220 desktop, 160 mobile)
 */
function GrafanaPanel({ title, src, externalUrl, aspectRatio = 16 / 9 }) {
  const [fullscreen, setFullscreen] = useState(false);

  return (
    <>
      <Card sx={{ height: '100%' }}>
        <CardHeader
          title={title}
          titleTypographyProps={{ variant: 'subtitle1' }}
          action={
            <Box>
              {externalUrl && (
                <Tooltip title="Open in Grafana">
                  <IconButton
                    size="small"
                    href={externalUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <OpenInNewIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title="Full screen">
                <IconButton size="small" onClick={() => setFullscreen(true)}>
                  <FullscreenIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>
          }
          sx={{ pb: 0 }}
        />
        <CardContent sx={{ pt: 1 }}>
          <GrafanaEmbed
            src={src}
            title={title}
            aspectRatio={aspectRatio}
            minHeight={{ xs: 160, md: 220 }}
          />
        </CardContent>
      </Card>

      {/* Full-screen dialog */}
      <Dialog
        open={fullscreen}
        onClose={() => setFullscreen(false)}
        maxWidth={false}
        fullWidth
        PaperProps={{
          sx: { m: 1, maxWidth: 'calc(100vw - 16px)', height: 'calc(100vh - 16px)' },
        }}
      >
        <DialogContent sx={{ p: 0, height: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Header bar */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              px: 2,
              py: 1,
              borderBottom: 1,
              borderColor: 'divider',
              flexShrink: 0,
            }}
          >
            <Box component="span" sx={{ typography: 'subtitle1' }}>
              {title}
            </Box>
            <Tooltip title="Exit full screen">
              <IconButton size="small" onClick={() => setFullscreen(false)}>
                <FullscreenExitIcon />
              </IconButton>
            </Tooltip>
          </Box>

          {/* Full-size embed */}
          <Box sx={{ flex: 1, overflow: 'hidden' }}>
            <GrafanaEmbed
              src={src}
              title={title}
              aspectRatio={undefined}
              sx={{ paddingTop: 0, height: '100%' }}
            />
          </Box>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default GrafanaPanel;
