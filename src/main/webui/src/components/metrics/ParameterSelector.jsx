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

import React, { useMemo, useState, useCallback } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  MenuItem,
  TextField,
  Typography,
  InputAdornment,
  Stack,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SearchIcon from '@mui/icons-material/Search';
import SelectAllIcon from '@mui/icons-material/SelectAll';
import DeselectIcon from '@mui/icons-material/Deselect';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

const AGGREGATION_MODES = [
  { value: 'MINUTE_AVERAGE', label: '1 min avg' },
  { value: 'MINUTE_CURRENT', label: '1 min current' },
  { value: 'MINUTE_DIFF', label: '1 min diff' },
  { value: 'HOUR_AVERAGE', label: '1 hr avg' },
  { value: 'HOUR_CURRENT', label: '1 hr current' },
  { value: 'HOUR_DIFF', label: '1 hr diff' },
  { value: 'DAY_AVERAGE', label: '1 day avg' },
  { value: 'DAY_CURRENT', label: '1 day current' },
  { value: 'DAY_DIFF', label: '1 day diff' },
];

/**
 * ParameterSelector - grouped parameter selection with search and select all/deselect all
 *
 * @param {Object} props
 * @param {Array} props.availableParameters - Available parameters from discovery
 * @param {Array} props.selectedParameters - Currently selected parameter keys (modelId_fieldName)
 * @param {Function} props.onSelectionChange - Callback when selection changes
 * @param {boolean} props.disabled - Whether the selector is disabled
 * @param {boolean} props.discoveryBased - Whether parameters came from live device discovery
 * @param {boolean} props.showModeSelector - Show aggregation mode dropdown per selected parameter
 * @param {Object} props.parameterModes - Map of paramKey → aggregation mode string
 * @param {Function} props.onModeChange - Callback(key, mode) when mode changes
 */
function ParameterSelector({
  availableParameters = [],
  selectedParameters = [],
  onSelectionChange,
  disabled = false,
  discoveryBased = true,
  showModeSelector = false,
  parameterModes = {},
  onModeChange = () => {},
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedModels, setExpandedModels] = useState({});

  // Group parameters by model
  const paramsByModel = useMemo(() => {
    const groups = {};
    for (const param of availableParameters) {
      const key = param.modelId;
      if (!groups[key]) {
        groups[key] = {
          modelId: param.modelId,
          modelName: param.modelName,
          fields: [],
        };
      }
      groups[key].fields.push(param);
    }
    return Object.values(groups).sort((a, b) => a.modelId - b.modelId);
  }, [availableParameters]);

  // Filter by search term
  const filteredGroups = useMemo(() => {
    if (!searchTerm.trim()) return paramsByModel;

    const term = searchTerm.toLowerCase();
    return paramsByModel
      .map((group) => ({
        ...group,
        fields: group.fields.filter(
          (f) =>
            f.fieldName.toLowerCase().includes(term) ||
            (f.description && f.description.toLowerCase().includes(term)) ||
            (f.units && f.units.toLowerCase().includes(term)) ||
            (f.metricName && f.metricName.toLowerCase().includes(term)) ||
            group.modelName.toLowerCase().includes(term)
        ),
      }))
      .filter((group) => group.fields.length > 0);
  }, [paramsByModel, searchTerm]);

  // Create a set for fast lookup
  const selectedSet = useMemo(() => new Set(selectedParameters), [selectedParameters]);

  const makeKey = (modelId, fieldName) => `${modelId}_${fieldName}`;

  const handleToggle = useCallback(
    (modelId, fieldName) => {
      const key = makeKey(modelId, fieldName);
      const newSelection = selectedSet.has(key)
        ? selectedParameters.filter((k) => k !== key)
        : [...selectedParameters, key];
      onSelectionChange(newSelection);
    },
    [selectedParameters, selectedSet, onSelectionChange]
  );

  const handleSelectAllModel = useCallback(
    (group) => {
      const modelKeys = group.fields.map((f) => makeKey(group.modelId, f.fieldName));
      const allSelected = modelKeys.every((k) => selectedSet.has(k));

      let newSelection;
      if (allSelected) {
        // Deselect all in this model
        const modelKeySet = new Set(modelKeys);
        newSelection = selectedParameters.filter((k) => !modelKeySet.has(k));
      } else {
        // Select all in this model
        const existing = new Set(selectedParameters);
        modelKeys.forEach((k) => existing.add(k));
        newSelection = [...existing];
      }
      onSelectionChange(newSelection);
    },
    [selectedParameters, selectedSet, onSelectionChange]
  );

  const handleSelectAll = useCallback(() => {
    const allKeys = availableParameters.map((p) => makeKey(p.modelId, p.fieldName));
    onSelectionChange(allKeys);
  }, [availableParameters, onSelectionChange]);

  const handleDeselectAll = useCallback(() => {
    onSelectionChange([]);
  }, [onSelectionChange]);

  const handleAccordionChange = useCallback(
    (modelId) => (_, isExpanded) => {
      setExpandedModels((prev) => ({ ...prev, [modelId]: isExpanded }));
    },
    []
  );

  const totalSelected = selectedParameters.length;
  const totalAvailable = availableParameters.length;

  return (
    <Box>
      {/* Discovery status warning */}
      {!discoveryBased && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Could not discover models from the device. Showing all known SunSpec models from the
          static registry. Parameters for models not present on the device will be
          automatically skipped during scraping.
        </Alert>
      )}

      {/* Model selection guide */}
      <Alert severity="info" icon={<InfoOutlinedIcon />} sx={{ mb: 2 }}>
        <Typography variant="body2" sx={{ fontWeight: 500, mb: 0.5 }}>
          Parameter selection guide
        </Typography>
        <Typography variant="caption" component="div" color="text.secondary">
          Each device supports only <strong>one data format</strong> (Int+SF or Float) and
          only <strong>one phase type</strong> (Single Phase, Split Phase, or Three Phase).
          For example, an inverter will report either model 103 (Three Phase, Int+SF) or 113
          (Three Phase, Float), but never both. Similarly, a meter provides only one of
          models 201-204 or 211-214 depending on its wiring and format.
          Parameters selected for models not present on the device are automatically filtered
          out at scrape time.{' '}
          <strong>Solar API Site</strong> parameters are sourced from the Fronius Solar API
          and are always available when Solar API is enabled, regardless of device discovery.
        </Typography>
      </Alert>

      {/* Search and bulk actions */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 2 }} alignItems="center">
        <TextField
          size="small"
          placeholder="Search parameters..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          disabled={disabled}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ flexGrow: 1 }}
        />
        <Stack direction="row" spacing={1}>
          <Button
            size="small"
            variant="outlined"
            startIcon={<SelectAllIcon />}
            onClick={handleSelectAll}
            disabled={disabled || totalSelected === totalAvailable}
          >
            All
          </Button>
          <Button
            size="small"
            variant="outlined"
            startIcon={<DeselectIcon />}
            onClick={handleDeselectAll}
            disabled={disabled || totalSelected === 0}
          >
            None
          </Button>
        </Stack>
      </Stack>

      {/* Selection summary */}
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {totalSelected} of {totalAvailable} parameters selected
      </Typography>

      {/* Grouped parameter list */}
      {filteredGroups.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
          {searchTerm ? 'No parameters match your search' : 'No parameters available'}
        </Typography>
      ) : (
        filteredGroups.map((group) => {
          const modelKeys = group.fields.map((f) => makeKey(group.modelId, f.fieldName));
          const selectedInModel = modelKeys.filter((k) => selectedSet.has(k)).length;
          const allSelected = selectedInModel === group.fields.length;

          return (
            <Accordion
              key={group.modelId}
              expanded={expandedModels[group.modelId] ?? false}
              onChange={handleAccordionChange(group.modelId)}
              disabled={disabled}
              disableGutters
              sx={{ '&:before': { display: 'none' } }}
            >
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%', mr: 1 }}>
                  <Typography variant="subtitle2" sx={{ flexGrow: 1 }}>
                    {group.modelName}{group.modelId >= 0 ? ` (Model ${group.modelId})` : ''}
                  </Typography>
                  <Chip
                    label={`${selectedInModel}/${group.fields.length}`}
                    size="small"
                    color={selectedInModel > 0 ? 'primary' : 'default'}
                    variant={allSelected ? 'filled' : 'outlined'}
                  />
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ pt: 0 }}>
                {/* Model-level select all */}
                <Box sx={{ mb: 1, borderBottom: 1, borderColor: 'divider', pb: 1 }}>
                  <Button
                    size="small"
                    onClick={() => handleSelectAllModel(group)}
                    disabled={disabled}
                  >
                    {allSelected ? 'Deselect All' : 'Select All'} in {group.modelName}
                  </Button>
                </Box>

                {/* Field checkboxes */}
                {group.fields.map((field) => {
                  const key = makeKey(group.modelId, field.fieldName);
                  const isChecked = selectedSet.has(key);

                  return (
                    <Box key={key} sx={{ pl: 1, display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={isChecked}
                            onChange={() => handleToggle(group.modelId, field.fieldName)}
                            disabled={disabled}
                            size="small"
                          />
                        }
                        label={
                          <Box>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                {field.fieldName}
                              </Typography>
                              {field.units && (
                                <Chip label={field.units} size="small" variant="outlined" sx={{ height: 20 }} />
                              )}
                              {field.description && (
                                <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: { xs: 150, sm: 300 } }}>
                                  {field.description}
                                </Typography>
                              )}
                            </Stack>
                            {field.metricName && (
                              <Typography
                                variant="caption"
                                color="text.disabled"
                                sx={{ fontFamily: 'monospace', fontSize: '0.7rem', display: 'block', mt: -0.25 }}
                              >
                                {field.metricName}
                              </Typography>
                            )}
                          </Box>
                        }
                        sx={{ flexGrow: 1 }}
                      />
                      {showModeSelector && isChecked && (
                        <TextField
                          select
                          size="small"
                          label="Aggregation"
                          value={parameterModes[key] || 'MINUTE_AVERAGE'}
                          onChange={(e) => onModeChange(key, e.target.value)}
                          disabled={disabled}
                          sx={{ minWidth: 140, mt: 0.5, flexShrink: 0 }}
                        >
                          {AGGREGATION_MODES.map((m) => (
                            <MenuItem key={m.value} value={m.value}>{m.label}</MenuItem>
                          ))}
                        </TextField>
                      )}
                    </Box>
                  );
                })}
              </AccordionDetails>
            </Accordion>
          );
        })
      )}
    </Box>
  );
}

export default ParameterSelector;
