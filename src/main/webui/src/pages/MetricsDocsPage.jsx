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

import React, { useState, useMemo } from 'react';
import {
  Card,
  CardBody,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Label,
  TextInput,
} from '@patternfly/react-core';
import { Table, Thead, Tbody, Tr, Th, Td } from '@patternfly/react-table';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import { useMetricsDocs } from '../hooks';

const C = {
  subtle: 'var(--pf-t--global--text--color--subtle, #6a6e73)',
};

/**
 * Category labels for display
 */
const CATEGORY_LABELS = {
  power: 'Power',
  current: 'Current',
  voltage: 'Voltage',
  energy: 'Energy',
  temperature: 'Temperature',
  status: 'Status',
  rating: 'Nameplate Ratings',
  setting: 'Settings',
  control: 'Controls',
  battery: 'Battery / Storage',
};

/**
 * Color mapping for category labels (MUI color → PF Label color)
 */
const CATEGORY_COLORS = {
  power: 'blue',      // primary → blue
  current: 'cyan',    // info → cyan
  voltage: 'orange',  // warning → orange
  energy: 'green',    // success → green
  temperature: 'red', // error → red
  status: 'grey',     // default → grey
  rating: 'purple',   // secondary → purple
  setting: 'purple',  // secondary → purple
  control: 'orange',  // warning → orange
  battery: 'cyan',    // info → cyan
};

/**
 * Metrics Documentation page
 */
function MetricsDocsPage() {
  const { data, isLoading, error } = useMetricsDocs();
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [searchText, setSearchText] = useState('');

  const categories = useMemo(() => {
    if (!data?.metrics) return [];
    const cats = [...new Set(data.metrics.map((m) => m.category))];
    cats.sort();
    return cats;
  }, [data]);

  const filteredMetrics = useMemo(() => {
    if (!data?.metrics) return [];
    let result = data.metrics;

    if (categoryFilter !== 'all') {
      result = result.filter((m) => m.category === categoryFilter);
    }

    if (searchText.trim()) {
      const term = searchText.toLowerCase();
      result = result.filter(
        (m) =>
          m.metricName.toLowerCase().includes(term) ||
          m.description.toLowerCase().includes(term) ||
          m.semanticName.toLowerCase().includes(term)
      );
    }

    return result;
  }, [data, categoryFilter, searchText]);

  if (isLoading) {
    return (
      <div>
        <PageHeader
          title="Metrics Documentation"
          subtitle="Semantic metric definitions and SunSpec field mappings"
        />
        <LoadingSpinner message="Loading metrics documentation..." />
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <PageHeader
          title="Metrics Documentation"
          subtitle="Semantic metric definitions and SunSpec field mappings"
        />
        <ErrorDisplay error={error} message="Failed to load metrics documentation" />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Metrics Documentation"
        subtitle={`${data?.metrics?.length || 0} semantic metrics with ISO base unit naming`}
      />

      {/* Filters */}
      <Card style={{ marginBottom: 24 }}>
        <CardBody>
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <FormGroup label="Search" fieldId="metrics-search" style={{ minWidth: 250 }}>
              <TextInput
                id="metrics-search"
                value={searchText}
                onChange={(_event, value) => setSearchText(value)}
                placeholder="Search metrics..."
                aria-label="Search metrics"
              />
            </FormGroup>
            <FormGroup label="Category" fieldId="metrics-category" style={{ minWidth: 180 }}>
              <FormSelect
                id="metrics-category"
                value={categoryFilter}
                onChange={(_event, value) => setCategoryFilter(value)}
                aria-label="Filter by category"
              >
                <FormSelectOption value="all" label="All categories" />
                {categories.map((cat) => (
                  <FormSelectOption
                    key={cat}
                    value={cat}
                    label={CATEGORY_LABELS[cat] || cat}
                  />
                ))}
              </FormSelect>
            </FormGroup>
            <span style={{ fontSize: '0.875rem', color: C.subtle, marginLeft: 'auto', alignSelf: 'center' }}>
              {filteredMetrics.length} metric{filteredMetrics.length !== 1 ? 's' : ''}
            </span>
          </div>
        </CardBody>
      </Card>

      {/* Metrics Table */}
      <Card>
        <Table aria-label="Metrics documentation" variant="compact">
          <Thead>
            <Tr>
              <Th>Metric Name</Th>
              <Th>Description</Th>
              <Th>Unit</Th>
              <Th>Category</Th>
              <Th>Fields / Tags</Th>
            </Tr>
          </Thead>
          <Tbody>
            {filteredMetrics.map((metric) => (
              <Tr key={metric.metricName}>
                <Td dataLabel="Metric Name">
                  <span style={{ fontFamily: 'monospace', fontWeight: 500, whiteSpace: 'nowrap', fontSize: '0.875rem' }}>
                    {metric.metricName}
                  </span>
                </Td>
                <Td dataLabel="Description">
                  <span style={{ fontSize: '0.875rem' }}>{metric.description}</span>
                </Td>
                <Td dataLabel="Unit">
                  {metric.baseUnit ? (
                    <Label color="grey" variant="outline">{metric.baseUnit}</Label>
                  ) : (
                    <span style={{ fontSize: '0.875rem', color: C.subtle }}>unitless</span>
                  )}
                </Td>
                <Td dataLabel="Category">
                  <Label color={CATEGORY_COLORS[metric.category] || 'grey'}>
                    {CATEGORY_LABELS[metric.category] || metric.category}
                  </Label>
                </Td>
                <Td dataLabel="Fields / Tags">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {metric.fields.map((f, idx) => (
                      <div key={idx} style={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
                        <span style={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{f.field}</span>
                        <span style={{ fontSize: '0.75rem', color: C.subtle }}>
                          (model {f.modelIds.join(', ')})
                        </span>
                        {Object.entries(f.tags || {}).map(([k, v]) => (
                          <Label
                            key={k}
                            color="grey"
                            variant="outline"
                            style={{ height: 20, fontSize: '0.7rem' }}
                          >
                            {k}={v}
                          </Label>
                        ))}
                      </div>
                    ))}
                  </div>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      </Card>
    </div>
  );
}

export default MetricsDocsPage;
