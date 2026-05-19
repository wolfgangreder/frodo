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
  AccordionItem,
  AccordionToggle,
  AccordionContent,
  Alert,
  Button,
  Checkbox,
  Flex,
  FlexItem,
  InputGroup,
  InputGroupItem,
  Label,
  MenuToggle,
  Select,
  SelectList,
  SelectOption,
  TextInput,
} from '@patternfly/react-core';
import {
  CheckSquareIcon,
  MinusCircleIcon,
  SearchIcon,
} from '@patternfly/react-icons';

const C = {
  primary:  'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:   'var(--pf-t--global--text-color--subtle, #6a6e73)',
  disabled: 'var(--pf-t--global--text-color--disabled, #b8bbbe)',
  border:   'var(--pf-t--global--border--color--default, #d2d2d2)',
};

const AGGREGATION_MODES = [
  { value: 'MINUTE_AVERAGE',  label: '1 min avg' },
  { value: 'MINUTE_CURRENT',  label: '1 min current' },
  { value: 'MINUTE_DIFF',     label: '1 min diff' },
  { value: 'HOUR_AVERAGE',    label: '1 hr avg' },
  { value: 'HOUR_CURRENT',    label: '1 hr current' },
  { value: 'HOUR_DIFF',       label: '1 hr diff' },
  { value: 'DAY_AVERAGE',     label: '1 day avg' },
  { value: 'DAY_CURRENT',     label: '1 day current' },
  { value: 'DAY_DIFF',        label: '1 day diff' },
];

// Extracted so each row has its own isOpen state without polluting parent
function AggregationSelect({ paramKey, value, onChange, disabled }) {
  const [isOpen, setIsOpen] = useState(false);
  const currentLabel = AGGREGATION_MODES.find((m) => m.value === value)?.label || value;
  return (
    <Select
      isOpen={isOpen}
      onSelect={(_e, v) => { onChange(paramKey, v); setIsOpen(false); }}
      onOpenChange={(o) => setIsOpen(o)}
      toggle={(ref) => (
        <MenuToggle
          ref={ref}
          onClick={() => setIsOpen(!isOpen)}
          isExpanded={isOpen}
          isDisabled={disabled}
          style={{ minWidth: 150 }}
        >
          {currentLabel}
        </MenuToggle>
      )}
    >
      <SelectList>
        {AGGREGATION_MODES.map((m) => (
          <SelectOption key={m.value} value={m.value}>{m.label}</SelectOption>
        ))}
      </SelectList>
    </Select>
  );
}

/**
 * ParameterSelector - grouped parameter selection with search and select all/deselect all
 *
 * @param {Object} props
 * @param {Array}    props.availableParameters  - Available parameters from discovery
 * @param {Array}    props.selectedParameters   - Selected parameter keys (modelId_fieldName)
 * @param {Function} props.onSelectionChange    - Callback when selection changes
 * @param {boolean}  props.disabled             - Whether selector is disabled
 * @param {boolean}  props.discoveryBased       - Parameters came from live device discovery
 * @param {boolean}  props.showModeSelector     - Show aggregation mode dropdown per selected param
 * @param {Object}   props.parameterModes       - Map of paramKey → aggregation mode string
 * @param {Function} props.onModeChange         - Callback(key, mode) when mode changes
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
        groups[key] = { modelId: param.modelId, modelName: param.modelName, fields: [] };
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
        const modelKeySet = new Set(modelKeys);
        newSelection = selectedParameters.filter((k) => !modelKeySet.has(k));
      } else {
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

  const toggleModel = useCallback((modelId) => {
    setExpandedModels((prev) => ({ ...prev, [modelId]: !(prev[modelId] ?? false) }));
  }, []);

  const totalSelected  = selectedParameters.length;
  const totalAvailable = availableParameters.length;

  return (
    <div>
      {/* Discovery status warning */}
      {!discoveryBased && (
        <Alert
          variant="warning"
          isInline
          title="Could not discover models from the device. Showing all known SunSpec models from the static registry. Parameters for models not present on the device will be automatically skipped during scraping."
          style={{ marginBottom: '1rem' }}
        />
      )}

      {/* Model selection guide */}
      <Alert
        variant="info"
        isInline
        title="Parameter selection guide"
        style={{ marginBottom: '1rem' }}
      >
        <div style={{ fontSize: '0.8rem', color: C.subtle }}>
          Each device supports only <strong>one data format</strong> (Int+SF or Float) and
          only <strong>one phase type</strong> (Single Phase, Split Phase, or Three Phase).
          For example, an inverter will report either model 103 (Three Phase, Int+SF) or 113
          (Three Phase, Float), but never both. Similarly, a meter provides only one of
          models 201-204 or 211-214 depending on its wiring and format.
          Parameters selected for models not present on the device are automatically filtered
          out at scrape time.{' '}
          <strong>Solar API Site</strong> parameters are sourced from the Fronius Solar API
          and are always available when Solar API is enabled, regardless of device discovery.
        </div>
      </Alert>

      {/* Search and bulk actions */}
      <Flex
        direction={{ default: 'column', sm: 'row' }}
        gap={{ default: 'gapSm' }}
        alignItems={{ sm: 'alignItemsCenter' }}
        style={{ marginBottom: '0.75rem' }}
      >
        <FlexItem grow={{ default: 'grow' }}>
          <InputGroup>
            <InputGroupItem>
              <Button variant="plain" aria-label="search" tabIndex={-1} isDisabled={disabled}>
                <SearchIcon />
              </Button>
            </InputGroupItem>
            <InputGroupItem isFill>
              <TextInput
                placeholder="Search parameters..."
                value={searchTerm}
                onChange={(_e, v) => setSearchTerm(v)}
                isDisabled={disabled}
                aria-label="Search parameters"
              />
            </InputGroupItem>
          </InputGroup>
        </FlexItem>
        <FlexItem>
          <Flex gap={{ default: 'gapSm' }}>
            <FlexItem>
              <Button
                variant="secondary"
                size="sm"
                icon={<CheckSquareIcon />}
                onClick={handleSelectAll}
                isDisabled={disabled || totalSelected === totalAvailable}
              >
                All
              </Button>
            </FlexItem>
            <FlexItem>
              <Button
                variant="secondary"
                size="sm"
                icon={<MinusCircleIcon />}
                onClick={handleDeselectAll}
                isDisabled={disabled || totalSelected === 0}
              >
                None
              </Button>
            </FlexItem>
          </Flex>
        </FlexItem>
      </Flex>

      {/* Selection summary */}
      <div style={{ fontSize: '0.875rem', color: C.subtle, marginBottom: '0.5rem' }}>
        {totalSelected} of {totalAvailable} parameters selected
      </div>

      {/* Grouped parameter list */}
      {filteredGroups.length === 0 ? (
        <div style={{ fontSize: '0.875rem', color: C.subtle, padding: '1rem 0', textAlign: 'center' }}>
          {searchTerm ? 'No parameters match your search' : 'No parameters available'}
        </div>
      ) : (
        <Accordion isBordered>
          {filteredGroups.map((group) => {
            const modelKeys      = group.fields.map((f) => makeKey(group.modelId, f.fieldName));
            const selectedInModel = modelKeys.filter((k) => selectedSet.has(k)).length;
            const allSelected    = selectedInModel === group.fields.length;
            const isExpanded     = expandedModels[group.modelId] ?? false;
            const toggleId       = `accordion-toggle-${group.modelId}`;
            const contentId      = `accordion-content-${group.modelId}`;

            return (
              <AccordionItem key={group.modelId} isExpanded={isExpanded}>
                <AccordionToggle
                  id={toggleId}
                  onClick={() => !disabled && toggleModel(group.modelId)}
                  aria-controls={contentId}
                >
                  <Flex
                    gap={{ default: 'gapSm' }}
                    alignItems={{ default: 'alignItemsCenter' }}
                    style={{ width: '100%', paddingRight: '0.5rem' }}
                  >
                    <FlexItem grow={{ default: 'grow' }}>
                      <span style={{ fontWeight: 600 }}>
                        {group.modelName}
                        {group.modelId >= 0 ? ` (Model ${group.modelId})` : ''}
                      </span>
                    </FlexItem>
                    <FlexItem>
                      <Label
                        color={selectedInModel > 0 ? 'blue' : 'grey'}
                        variant={allSelected ? 'filled' : 'outline'}
                      >
                        {selectedInModel}/{group.fields.length}
                      </Label>
                    </FlexItem>
                  </Flex>
                </AccordionToggle>

                <AccordionContent id={contentId}>
                  {/* Model-level select all */}
                  <div style={{ marginBottom: '0.5rem', borderBottom: `1px solid ${C.border}`, paddingBottom: '0.5rem' }}>
                    <Button
                      variant="link"
                      size="sm"
                      onClick={() => handleSelectAllModel(group)}
                      isDisabled={disabled}
                    >
                      {allSelected ? 'Deselect All' : 'Select All'} in {group.modelName}
                    </Button>
                  </div>

                  {/* Field checkboxes */}
                  {group.fields.map((field) => {
                    const key       = makeKey(group.modelId, field.fieldName);
                    const isChecked = selectedSet.has(key);

                    return (
                      <div
                        key={key}
                        style={{
                          paddingLeft: '0.5rem',
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: '0.5rem',
                          marginBottom: '0.25rem',
                        }}
                      >
                        <div style={{ flexGrow: 1 }}>
                          <Checkbox
                            id={`param-${key}`}
                            isChecked={isChecked}
                            onChange={() => handleToggle(group.modelId, field.fieldName)}
                            isDisabled={disabled}
                            label={
                              <div>
                                <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
                                  <FlexItem>
                                    <span style={{ fontWeight: 500, fontSize: '0.875rem' }}>
                                      {field.fieldName}
                                    </span>
                                  </FlexItem>
                                  {field.units && (
                                    <FlexItem>
                                      <Label variant="outline" style={{ height: 20, fontSize: '0.7rem' }}>
                                        {field.units}
                                      </Label>
                                    </FlexItem>
                                  )}
                                  {field.description && (
                                    <FlexItem>
                                      <span
                                        style={{
                                          fontSize: '0.75rem',
                                          color: C.subtle,
                                          whiteSpace: 'nowrap',
                                          overflow: 'hidden',
                                          textOverflow: 'ellipsis',
                                          maxWidth: 300,
                                          display: 'inline-block',
                                          verticalAlign: 'middle',
                                        }}
                                      >
                                        {field.description}
                                      </span>
                                    </FlexItem>
                                  )}
                                </Flex>
                                {field.metricName && (
                                  <div style={{ fontFamily: 'monospace', fontSize: '0.7rem', color: C.disabled, marginTop: '-0.125rem' }}>
                                    {field.metricName}
                                  </div>
                                )}
                              </div>
                            }
                          />
                        </div>
                        {showModeSelector && isChecked && (
                          <div style={{ flexShrink: 0, marginTop: '0.25rem' }}>
                            <AggregationSelect
                              paramKey={key}
                              value={parameterModes[key] || 'MINUTE_AVERAGE'}
                              onChange={onModeChange}
                              disabled={disabled}
                            />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </AccordionContent>
              </AccordionItem>
            );
          })}
        </Accordion>
      )}
    </div>
  );
}

export default ParameterSelector;
