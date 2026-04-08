import React from 'react';
import {
  Box,
  Slider,
  TextField,
  Typography,
  Stack,
} from '@mui/material';

const INTERVAL_MARKS = [
  { value: 1, label: '1s' },
  { value: 5, label: '5s' },
  { value: 15, label: '15s' },
  { value: 30, label: '30s' },
  { value: 60, label: '1m' },
  { value: 120, label: '2m' },
  { value: 300, label: '5m' },
];

/**
 * ScrapingIntervalInput - slider + number input for scrape interval
 *
 * @param {Object} props
 * @param {number} props.value - Current interval in seconds
 * @param {Function} props.onChange - Callback with new value
 * @param {boolean} props.disabled - Whether the input is disabled
 */
function ScrapingIntervalInput({ value = 30, onChange, disabled = false }) {
  const handleSliderChange = (_, newValue) => {
    onChange(newValue);
  };

  const handleInputChange = (e) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 1 && val <= 300) {
      onChange(val);
    }
  };

  const formatLabel = (val) => {
    if (val >= 60) {
      const mins = Math.floor(val / 60);
      const secs = val % 60;
      return secs > 0 ? `${mins}m ${secs}s` : `${mins}m`;
    }
    return `${val}s`;
  };

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        Scrape Interval
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
        How often to read SunSpec data from the device ({formatLabel(value)})
      </Typography>
      <Stack direction="row" spacing={2} alignItems="center">
        <Slider
          value={value}
          onChange={handleSliderChange}
          min={1}
          max={300}
          step={1}
          marks={INTERVAL_MARKS}
          valueLabelDisplay="auto"
          valueLabelFormat={formatLabel}
          disabled={disabled}
          sx={{ flexGrow: 1 }}
        />
        <TextField
          value={value}
          onChange={handleInputChange}
          type="number"
          size="small"
          disabled={disabled}
          slotProps={{
            input: { min: 1, max: 300, step: 1 },
            htmlInput: { min: 1, max: 300, step: 1 },
          }}
          sx={{ width: 90 }}
          helperText="seconds"
        />
      </Stack>
    </Box>
  );
}

export default ScrapingIntervalInput;
