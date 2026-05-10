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
  Box,
  Card,
  CardContent,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { PageHeader, LoadingSpinner, ErrorDisplay } from '../components/common';
import { useMetricsDocs } from '../hooks';

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
 * Color mapping for category chips
 */
const CATEGORY_COLORS = {
  power: 'primary',
  current: 'info',
  voltage: 'warning',
  energy: 'success',
  temperature: 'error',
  status: 'default',
  rating: 'secondary',
  setting: 'secondary',
  control: 'warning',
  battery: 'info',
};

/**
 * Metrics Documentation page - displays semantic metric definitions,
 * descriptions, units, and SunSpec field mappings.
 */
function MetricsDocsPage() {
  const { data, isLoading, error } = useMetricsDocs();
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [searchText, setSearchText] = useState('');

  // Extract unique categories
  const categories = useMemo(() => {
    if (!data?.metrics) return [];
    const cats = [...new Set(data.metrics.map((m) => m.category))];
    cats.sort();
    return cats;
  }, [data]);

  // Filter metrics
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
      <Box>
        <PageHeader
          title="Metrics Documentation"
          subtitle="Semantic metric definitions and SunSpec field mappings"
        />
        <LoadingSpinner message="Loading metrics documentation..." />
      </Box>
    );
  }

  if (error) {
    return (
      <Box>
        <PageHeader
          title="Metrics Documentation"
          subtitle="Semantic metric definitions and SunSpec field mappings"
        />
        <ErrorDisplay error={error} message="Failed to load metrics documentation" />
      </Box>
    );
  }

  return (
    <Box>
      <PageHeader
        title="Metrics Documentation"
        subtitle={`${data?.metrics?.length || 0} semantic metrics with ISO base unit naming`}
      />

      {/* Filters */}
      <Card sx={{ mb: 3 }}>
        <CardContent sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
          <TextField
            size="small"
            label="Search metrics"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            sx={{ minWidth: 250 }}
          />
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>Category</InputLabel>
            <Select
              value={categoryFilter}
              label="Category"
              onChange={(e) => setCategoryFilter(e.target.value)}
            >
              <MenuItem value="all">All categories</MenuItem>
              {categories.map((cat) => (
                <MenuItem key={cat} value={cat}>
                  {CATEGORY_LABELS[cat] || cat}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Typography variant="body2" color="text.secondary" sx={{ ml: 'auto' }}>
            {filteredMetrics.length} metric{filteredMetrics.length !== 1 ? 's' : ''}
          </Typography>
        </CardContent>
      </Card>

      {/* Metrics Table */}
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Metric Name</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Description</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Unit</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Category</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Fields / Tags</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredMetrics.map((metric) => (
                <TableRow key={metric.metricName} hover>
                  <TableCell>
                    <Typography
                      variant="body2"
                      sx={{ fontFamily: 'monospace', fontWeight: 500, whiteSpace: 'nowrap' }}
                    >
                      {metric.metricName}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{metric.description}</Typography>
                  </TableCell>
                  <TableCell>
                    {metric.baseUnit ? (
                      <Chip label={metric.baseUnit} size="small" variant="outlined" />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        unitless
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={CATEGORY_LABELS[metric.category] || metric.category}
                      size="small"
                      color={CATEGORY_COLORS[metric.category] || 'default'}
                    />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                      {metric.fields.map((f, idx) => (
                        <Box key={idx} sx={{ display: 'flex', gap: 0.5, alignItems: 'center', flexWrap: 'wrap' }}>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            {f.field}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            (model {f.modelIds.join(', ')})
                          </Typography>
                          {Object.entries(f.tags || {}).map(([k, v]) => (
                            <Chip
                              key={k}
                              label={`${k}=${v}`}
                              size="small"
                              variant="outlined"
                              sx={{ height: 20, fontSize: '0.7rem' }}
                            />
                          ))}
                        </Box>
                      ))}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </Box>
  );
}

export default MetricsDocsPage;
