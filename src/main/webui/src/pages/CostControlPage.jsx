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
import { formatForDisplay, nowAsDateTimeLocalValue, toDateTimeLocalValue, fromDateTimeLocalValue } from '../utils/timeZone';
import {
  Alert,
  Button,
  Card,
  CardBody,
  Divider,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Grid,
  GridItem,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Spinner,
  Tab,
  Tabs,
  TabTitleText,
  TextInput,
  Tooltip,
} from '@patternfly/react-core';
import {
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
} from '@patternfly/react-table';
import {
  PlusIcon,
  TrashIcon,
  PencilAltIcon,
  SyncAltIcon,
} from '@patternfly/react-icons';
import { PageHeader } from '../components/common';
import {
  useCostControlConfig,
  useUpdateCostControlConfig,
  useCostControlProviders,
  useCostControlPrices,
  useRefreshCostControlPrices,
  useMonthlyCosts,
  useDailyCosts,
  useHourlyCosts,
  useTariffWindows,
  useCreateTariffWindow,
  useUpdateTariffWindow,
  useDeleteTariffWindow,
  useGridFees,
  useCreateGridFee,
  useUpdateGridFee,
  useDeleteGridFee,
  useFixedCosts,
  useCreateFixedCost,
  useUpdateFixedCost,
  useDeleteFixedCost,
} from '../hooks/useCostControl';

// ---- Helpers ---------------------------------------------------------------

function fmtEur(val) {
  if (val == null || isNaN(val)) return '—';
  return `€ ${val.toFixed(2)}`;
}

function fmtKwh(val) {
  if (val == null || isNaN(val)) return '—';
  return `${val.toFixed(3)} kWh`;
}

function fmtCt(val) {
  if (val == null || isNaN(val)) return '—';
  return `${val.toFixed(4)} ct/kWh`;
}

/**
 * Unified direction/applies-to chip.
 * IMPORT = warning (orange), EXPORT = success (green), BOTH = default (grey).
 */
function DirectionChip({ value }) {
  const normalized = (value ?? 'BOTH').toUpperCase();
  const color = normalized === 'IMPORT' ? 'orange' : normalized === 'EXPORT' ? 'green' : 'grey';
  const label = normalized === 'IMPORT' ? 'Import' : normalized === 'EXPORT' ? 'Export' : 'Both';
  return <Label color={color}>{label}</Label>;
}

// ---- Monthly summary tab ---------------------------------------------------

function MonthlySummaryTab() {
  const { data: months, isLoading, error } = useMonthlyCosts();

  if (isLoading) return <Spinner style={{ marginTop: 16 }} />;
  if (error) return <Alert variant="danger" isInline title="Failed to load monthly costs" style={{ marginTop: 16 }} />;
  if (!months?.length) return (
    <Alert variant="info" isInline style={{ marginTop: 16 }}
      title="No monthly cost data yet. Data accumulates automatically as hourly records are processed." />
  );

  return (
    <div style={{ marginTop: 16, overflowX: 'auto' }}>
      <Table aria-label="Monthly cost summary">
        <Thead>
          <Tr>
            <Th>Month</Th>
            <Th style={{ textAlign: 'right' }}>Import kWh</Th>
            <Th style={{ textAlign: 'right' }}>Export kWh</Th>
            <Th style={{ textAlign: 'right' }}>Import Cost</Th>
            <Th style={{ textAlign: 'right' }}>Export Income</Th>
            <Th style={{ textAlign: 'right' }}>Fees</Th>
            <Th style={{ textAlign: 'right' }}>Fixed</Th>
            <Th style={{ textAlign: 'right' }}>Net Cost</Th>
            <Th style={{ textAlign: 'right' }}>Hours</Th>
          </Tr>
        </Thead>
        <Tbody>
          {months.map((m) => (
            <Tr key={m.yearMonth}>
              <Td dataLabel="Month">{m.yearMonth}</Td>
              <Td dataLabel="Import kWh" style={{ textAlign: 'right' }}>{fmtKwh(m.totalImportKwh)}</Td>
              <Td dataLabel="Export kWh" style={{ textAlign: 'right' }}>{fmtKwh(m.totalExportKwh)}</Td>
              <Td dataLabel="Import Cost" style={{ textAlign: 'right' }}>{fmtEur(m.totalImportCostEur)}</Td>
              <Td dataLabel="Export Income" style={{ textAlign: 'right' }}>{fmtEur(m.totalExportIncomeEur)}</Td>
              <Td dataLabel="Fees" style={{ textAlign: 'right' }}>{fmtEur(m.totalFeeEur)}</Td>
              <Td dataLabel="Fixed" style={{ textAlign: 'right' }}>{fmtEur(m.fixedCostEur)}</Td>
              <Td dataLabel="Net Cost" style={{ textAlign: 'right', fontWeight: 700 }}>{fmtEur(m.netCostEur)}</Td>
              <Td dataLabel="Hours" style={{ textAlign: 'right' }}>{m.hoursCalculated}</Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
    </div>
  );
}

// ---- Daily summary tab -----------------------------------------------------

function DailySummaryTab() {
  const today = new Date();
  const thirtyDaysAgo = new Date(today);
  thirtyDaysAgo.setDate(today.getDate() - 30);
  const todayStr = today.toISOString().slice(0, 10);
  const thirtyDaysAgoStr = thirtyDaysAgo.toISOString().slice(0, 10);
  // Backend 'to' is exclusive; add 1 day so displayed "to" date is included.
  const toExclusive = (dateStr) => {
    const d = new Date(dateStr + 'T00:00:00');
    d.setDate(d.getDate() + 1);
    return d.toISOString().slice(0, 10);
  };
  const [from, setFrom] = useState(() => thirtyDaysAgoStr);
  const [to, setTo] = useState(() => todayStr);
  const [query, setQuery] = useState({
    from: thirtyDaysAgoStr,
    to: toExclusive(todayStr),
  });

  const { data: rows, isLoading, error } = useDailyCosts(query.from, query.to);

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end', marginBottom: 16 }}>
        <FormGroup label="From" fieldId="daily-from">
          <TextInput id="daily-from" type="date" value={from} onChange={(_e, v) => setFrom(v)} />
        </FormGroup>
        <FormGroup label="To (inclusive)" fieldId="daily-to">
          <TextInput id="daily-to" type="date" value={to} onChange={(_e, v) => setTo(v)} />
        </FormGroup>
        <Button variant="secondary" onClick={() => setQuery({ from, to: toExclusive(to) })}>Load</Button>
      </div>
      {isLoading && <Spinner />}
      {error && <Alert variant="danger" isInline title="Failed to load daily costs" />}
      {rows && (
        <div style={{ overflowX: 'auto' }}>
          <Table aria-label="Daily cost summary">
            <Thead>
              <Tr>
                <Th>Day</Th>
                <Th style={{ textAlign: 'right' }}>Import kWh</Th>
                <Th style={{ textAlign: 'right' }}>Export kWh</Th>
                <Th style={{ textAlign: 'right' }}>Import Cost</Th>
                <Th style={{ textAlign: 'right' }}>Export Income</Th>
                <Th style={{ textAlign: 'right' }}>Fees</Th>
                <Th style={{ textAlign: 'right' }}>Net Cost</Th>
                <Th style={{ textAlign: 'right' }}>Hours</Th>
              </Tr>
            </Thead>
            <Tbody>
              {rows.map((r) => (
                <Tr key={r.day}>
                  <Td dataLabel="Day">{r.day}</Td>
                  <Td dataLabel="Import kWh" style={{ textAlign: 'right' }}>{fmtKwh(r.totalImportKwh)}</Td>
                  <Td dataLabel="Export kWh" style={{ textAlign: 'right' }}>{fmtKwh(r.totalExportKwh)}</Td>
                  <Td dataLabel="Import Cost" style={{ textAlign: 'right' }}>{fmtEur(r.totalImportCostEur)}</Td>
                  <Td dataLabel="Export Income" style={{ textAlign: 'right' }}>{fmtEur(r.totalExportIncomeEur)}</Td>
                  <Td dataLabel="Fees" style={{ textAlign: 'right' }}>{fmtEur(r.totalFeeEur)}</Td>
                  <Td dataLabel="Net Cost" style={{ textAlign: 'right', fontWeight: 700 }}>{fmtEur(r.netCostEur)}</Td>
                  <Td dataLabel="Hours" style={{ textAlign: 'right' }}>{r.hoursCalculated}</Td>
                </Tr>
              ))}
              {rows.length === 0 && (
                <Tr>
                  <Td colSpan={8} style={{ textAlign: 'center', color: 'var(--pf-t--global--text--color--subtle, #6a6e73)' }}>
                    No data in range
                  </Td>
                </Tr>
              )}
            </Tbody>
          </Table>
        </div>
      )}
    </div>
  );
}

// ---- Hourly cost tab -------------------------------------------------------

function HourlyCostTab() {
  const [from, setFrom] = useState(() => toDateTimeLocalValue(new Date(Date.now() - 24 * 60 * 60 * 1000)));
  const [to, setTo] = useState(nowAsDateTimeLocalValue);
  const [query, setQuery] = useState(() => ({
    from: fromDateTimeLocalValue(toDateTimeLocalValue(new Date(Date.now() - 24 * 60 * 60 * 1000))),
    to: fromDateTimeLocalValue(nowAsDateTimeLocalValue()),
  }));

  const { data: rows, isLoading, error } = useHourlyCosts(query.from, query.to);

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-end', marginBottom: 16 }}>
        <FormGroup label="From" fieldId="hourly-from">
          <TextInput id="hourly-from" type="datetime-local" value={from} onChange={(_e, v) => setFrom(v)} />
        </FormGroup>
        <FormGroup label="To" fieldId="hourly-to">
          <TextInput id="hourly-to" type="datetime-local" value={to} onChange={(_e, v) => setTo(v)} />
        </FormGroup>
        <Button variant="secondary"
          onClick={() => setQuery({ from: fromDateTimeLocalValue(from), to: fromDateTimeLocalValue(to) })}>
          Load
        </Button>
      </div>
      {isLoading && <Spinner />}
      {error && <Alert variant="danger" isInline title="Failed to load hourly costs" />}
      {rows && (
        <div style={{ overflowX: 'auto' }}>
          <Table aria-label="Hourly cost data">
            <Thead>
              <Tr>
                <Th>Hour</Th>
                <Th style={{ textAlign: 'right' }}>Import kWh</Th>
                <Th style={{ textAlign: 'right' }}>Export kWh</Th>
                <Th style={{ textAlign: 'right' }}>Import Price</Th>
                <Th style={{ textAlign: 'right' }}>Export Price</Th>
                <Th style={{ textAlign: 'right' }}>Import Cost</Th>
                <Th style={{ textAlign: 'right' }}>Export Income</Th>
                <Th style={{ textAlign: 'right' }}>Fees</Th>
                <Th style={{ textAlign: 'right' }}>Net</Th>
              </Tr>
            </Thead>
            <Tbody>
              {rows.map((r) => (
                <Tr key={r.hourStart}>
                  <Td dataLabel="Hour">{formatForDisplay(r.hourStart)}</Td>
                  <Td dataLabel="Import kWh" style={{ textAlign: 'right' }}>{fmtKwh(r.importKwh)}</Td>
                  <Td dataLabel="Export kWh" style={{ textAlign: 'right' }}>{fmtKwh(r.exportKwh)}</Td>
                  <Td dataLabel="Import Price" style={{ textAlign: 'right' }}>{fmtCt(r.priceImportCt)}</Td>
                  <Td dataLabel="Export Price" style={{ textAlign: 'right' }}>{fmtCt(r.priceExportCt)}</Td>
                  <Td dataLabel="Import Cost" style={{ textAlign: 'right' }}>{fmtEur(r.importCostEur)}</Td>
                  <Td dataLabel="Export Income" style={{ textAlign: 'right' }}>{fmtEur(r.exportIncomeEur)}</Td>
                  <Td dataLabel="Fees" style={{ textAlign: 'right' }}>{fmtEur(r.feeEur)}</Td>
                  <Td dataLabel="Net" style={{ textAlign: 'right', fontWeight: 700 }}>{fmtEur(r.netCostEur)}</Td>
                </Tr>
              ))}
              {rows.length === 0 && (
                <Tr>
                  <Td colSpan={9} style={{ textAlign: 'center', color: 'var(--pf-t--global--text--color--subtle, #6a6e73)' }}>
                    No data in range
                  </Td>
                </Tr>
              )}
            </Tbody>
          </Table>
        </div>
      )}
    </div>
  );
}

// ---- Energy prices tab -----------------------------------------------------

function EnergyPricesTab() {
  const { data: prices, isLoading, error } = useCostControlPrices(48);
  const refreshMutation = useRefreshCostControlPrices();

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <Button
          variant="secondary"
          icon={<SyncAltIcon />}
          onClick={() => refreshMutation.mutate('IMPORT')}
          isDisabled={refreshMutation.isPending}
        >
          Refresh Import
        </Button>
        <Button
          variant="secondary"
          icon={<SyncAltIcon />}
          onClick={() => refreshMutation.mutate('EXPORT')}
          isDisabled={refreshMutation.isPending}
        >
          Refresh Export
        </Button>
      </div>
      {isLoading && <Spinner />}
      {error && <Alert variant="danger" isInline title="Failed to load prices" />}
      {prices && (
        <div style={{ overflowX: 'auto' }}>
          <Table aria-label="Energy prices">
            <Thead>
              <Tr>
                <Th>Hour Start</Th>
                <Th style={{ textAlign: 'right' }}>Import Price</Th>
                <Th>Import Source</Th>
                <Th style={{ textAlign: 'right' }}>Export Price</Th>
                <Th>Export Source</Th>
              </Tr>
            </Thead>
            <Tbody>
              {prices.map((p) => (
                <Tr key={p.startTime}>
                  <Td dataLabel="Hour Start">{formatForDisplay(p.startTime)}</Td>
                  <Td dataLabel="Import Price" style={{ textAlign: 'right' }}>
                    {p.priceImportCt != null ? fmtCt(p.priceImportCt) : '—'}
                  </Td>
                  <Td dataLabel="Import Source">{p.importSource ?? '—'}</Td>
                  <Td dataLabel="Export Price" style={{ textAlign: 'right' }}>
                    {p.priceExportCt != null ? fmtCt(p.priceExportCt) : '—'}
                  </Td>
                  <Td dataLabel="Export Source">{p.exportSource ?? '—'}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </div>
      )}
    </div>
  );
}

// ---- Tariff windows tab ----------------------------------------------------

function TariffWindowForm({ initial, onSave, onCancel }) {
  const [form, setForm] = useState(initial || {
    direction: 'IMPORT',
    validFrom: '',
    validTo: '',
    daysOfWeek: '',
    timeFrom: '00:00:00',
    timeTo: '00:00:00',
    priceCt: 0,
    priority: 0,
    description: '',
  });

  const set = (field) => (_e, v) => setForm((f) => ({ ...f, [field]: v }));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, paddingTop: 8 }}>
      <FormGroup label="Direction" fieldId="tw-direction">
        <FormSelect id="tw-direction" value={form.direction} onChange={set('direction')}>
          <FormSelectOption value="IMPORT" label="Import" />
          <FormSelectOption value="EXPORT" label="Export" />
        </FormSelect>
      </FormGroup>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <FormGroup label="Valid From (yyyy-MM-dd)" fieldId="tw-valid-from" style={{ flex: 1 }}>
          <TextInput id="tw-valid-from" value={form.validFrom} onChange={set('validFrom')} />
        </FormGroup>
        <FormGroup label="Valid To (yyyy-MM-dd, empty = active)" fieldId="tw-valid-to" style={{ flex: 1 }}>
          <TextInput id="tw-valid-to" value={form.validTo} onChange={set('validTo')} />
        </FormGroup>
      </div>
      <FormGroup label="Days of Week (e.g. MON,TUE,WED,THU,FRI — empty = all)" fieldId="tw-days">
        <TextInput id="tw-days" value={form.daysOfWeek} onChange={set('daysOfWeek')} />
      </FormGroup>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <FormGroup label="Time From (HH:mm:ss)" fieldId="tw-time-from" style={{ flex: 1 }}>
          <TextInput id="tw-time-from" value={form.timeFrom} onChange={set('timeFrom')} />
        </FormGroup>
        <FormGroup label="Time To (HH:mm:ss, 00:00:00 = end-of-day)" fieldId="tw-time-to" style={{ flex: 1 }}>
          <TextInput id="tw-time-to" value={form.timeTo} onChange={set('timeTo')} />
        </FormGroup>
      </div>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <FormGroup label="Price (ct/kWh)" fieldId="tw-price" style={{ flex: 1 }}>
          <TextInput id="tw-price" type="number" value={String(form.priceCt)} onChange={set('priceCt')} />
        </FormGroup>
        <FormGroup label="Priority" fieldId="tw-priority" style={{ flex: 1 }}>
          <TextInput id="tw-priority" type="number" value={String(form.priority)} onChange={set('priority')} />
        </FormGroup>
      </div>
      <FormGroup label="Description" fieldId="tw-desc">
        <TextInput id="tw-desc" value={form.description} onChange={set('description')} />
      </FormGroup>
      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
        <Button variant="link" onClick={onCancel}>Cancel</Button>
        <Button variant="primary" onClick={() => onSave({
          ...form,
          priceCt: parseFloat(form.priceCt),
          priority: parseInt(form.priority, 10),
        })}>
          Save
        </Button>
      </div>
    </div>
  );
}

function TariffWindowsTab() {
  const { data: windows, isLoading, error } = useTariffWindows();
  const createMut = useCreateTariffWindow();
  const updateMut = useUpdateTariffWindow();
  const deleteMut = useDeleteTariffWindow();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);

  const handleSave = (form) => {
    if (editing) {
      updateMut.mutate({ id: editing.id, payload: form }, {
        onSuccess: () => { setDialogOpen(false); setEditing(null); },
      });
    } else {
      createMut.mutate(form, {
        onSuccess: () => setDialogOpen(false),
      });
    }
  };

  const openCreate = () => { setEditing(null); setDialogOpen(true); };
  const openEdit = (w) => { setEditing(w); setDialogOpen(true); };
  const closeDialog = () => { setDialogOpen(false); setEditing(null); };

  return (
    <div style={{ marginTop: 16 }}>
      <Button variant="primary" icon={<PlusIcon />} onClick={openCreate} style={{ marginBottom: 16 }}>
        Add Tariff Window
      </Button>
      {isLoading && <Spinner />}
      {error && <Alert variant="danger" isInline title="Failed to load tariff windows" />}
      {windows && (
        <div style={{ overflowX: 'auto' }}>
          <Table aria-label="Tariff windows">
            <Thead>
              <Tr>
                <Th>Direction</Th>
                <Th>Valid From</Th>
                <Th>Valid To</Th>
                <Th>Days</Th>
                <Th>Time From</Th>
                <Th>Time To</Th>
                <Th style={{ textAlign: 'right' }}>Price (ct/kWh)</Th>
                <Th style={{ textAlign: 'right' }}>Priority</Th>
                <Th>Description</Th>
                <Th />
              </Tr>
            </Thead>
            <Tbody>
              {windows.map((w) => (
                <Tr key={w.id}>
                  <Td dataLabel="Direction"><DirectionChip value={w.direction} /></Td>
                  <Td dataLabel="Valid From">{w.validFrom}</Td>
                  <Td dataLabel="Valid To">{w.validTo ?? '—'}</Td>
                  <Td dataLabel="Days">{w.daysOfWeek ?? 'All'}</Td>
                  <Td dataLabel="Time From">{w.timeFrom}</Td>
                  <Td dataLabel="Time To">{w.timeTo}</Td>
                  <Td dataLabel="Price (ct/kWh)" style={{ textAlign: 'right' }}>{w.priceCt.toFixed(4)}</Td>
                  <Td dataLabel="Priority" style={{ textAlign: 'right' }}>{w.priority}</Td>
                  <Td dataLabel="Description">{w.description ?? ''}</Td>
                  <Td>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <Tooltip content="Edit">
                        <Button variant="plain" aria-label="Edit" onClick={() => openEdit(w)}>
                          <PencilAltIcon />
                        </Button>
                      </Tooltip>
                      <Tooltip content="Delete">
                        <Button variant="plain" aria-label="Delete" onClick={() => deleteMut.mutate(w.id)}>
                          <TrashIcon />
                        </Button>
                      </Tooltip>
                    </div>
                  </Td>
                </Tr>
              ))}
              {windows.length === 0 && (
                <Tr>
                  <Td colSpan={10} style={{ textAlign: 'center', color: 'var(--pf-t--global--text--color--subtle, #6a6e73)' }}>
                    No tariff windows configured
                  </Td>
                </Tr>
              )}
            </Tbody>
          </Table>
        </div>
      )}
      <Modal isOpen={dialogOpen} onClose={closeDialog} variant="medium">
        <ModalHeader title={editing ? 'Edit Tariff Window' : 'Add Tariff Window'} onClose={closeDialog} />
        <ModalBody>
          <TariffWindowForm initial={editing} onSave={handleSave} onCancel={closeDialog} />
        </ModalBody>
      </Modal>
    </div>
  );
}

// ---- Grid fees tab ---------------------------------------------------------

function GridFeeForm({ initial, onSave, onCancel }) {
  const [form, setForm] = useState(initial || {
    validFrom: '',
    feeType: 'ABSOLUTE_ENERGY',
    feeValue: 0,
    appliesTo: 'BOTH',
    description: '',
  });

  const set = (field) => (_e, v) => setForm((f) => ({ ...f, [field]: v }));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, paddingTop: 8 }}>
      <FormGroup label="Valid From (ISO, e.g. 2026-01-01T00:00:00)" fieldId="gf-valid-from">
        <TextInput id="gf-valid-from" value={form.validFrom} onChange={set('validFrom')} />
      </FormGroup>
      <FormGroup label="Fee Type" fieldId="gf-fee-type">
        <FormSelect id="gf-fee-type" value={form.feeType} onChange={set('feeType')}>
          <FormSelectOption value="PERCENT" label="Percent of base cost (%)" />
          <FormSelectOption value="ABSOLUTE_ENERGY" label="Absolute per kWh (ct/kWh)" />
          <FormSelectOption value="ABSOLUTE_TIME" label="Absolute per month (EUR/month)" />
        </FormSelect>
      </FormGroup>
      <FormGroup label="Fee Value" fieldId="gf-fee-value">
        <TextInput id="gf-fee-value" type="number" value={String(form.feeValue)} onChange={set('feeValue')} />
      </FormGroup>
      <FormGroup label="Applies To" fieldId="gf-applies-to">
        <FormSelect id="gf-applies-to" value={form.appliesTo} onChange={set('appliesTo')}>
          <FormSelectOption value="IMPORT" label="Import only" />
          <FormSelectOption value="EXPORT" label="Export only" />
          <FormSelectOption value="BOTH" label="Both" />
        </FormSelect>
      </FormGroup>
      <FormGroup label="Description" fieldId="gf-desc">
        <TextInput id="gf-desc" value={form.description} onChange={set('description')} />
      </FormGroup>
      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
        <Button variant="link" onClick={onCancel}>Cancel</Button>
        <Button variant="primary" onClick={() => onSave({ ...form, feeValue: parseFloat(form.feeValue) })}>
          Save
        </Button>
      </div>
    </div>
  );
}

function GridFeesTab() {
  const { data: fees, isLoading, error } = useGridFees();
  const createMut = useCreateGridFee();
  const updateMut = useUpdateGridFee();
  const deleteMut = useDeleteGridFee();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);

  const handleSave = (form) => {
    if (editing) {
      updateMut.mutate({ id: editing.id, payload: form }, {
        onSuccess: () => { setDialogOpen(false); setEditing(null); },
      });
    } else {
      createMut.mutate(form, { onSuccess: () => setDialogOpen(false) });
    }
  };

  const openCreate = () => { setEditing(null); setDialogOpen(true); };
  const openEdit = (f) => { setEditing(f); setDialogOpen(true); };
  const closeDialog = () => { setDialogOpen(false); setEditing(null); };

  return (
    <div style={{ marginTop: 16 }}>
      <Button variant="primary" icon={<PlusIcon />} onClick={openCreate} style={{ marginBottom: 16 }}>
        Add Grid Fee
      </Button>
      {isLoading && <Spinner />}
      {error && <Alert variant="danger" isInline title="Failed to load grid fees" />}
      {fees && (
        <div style={{ overflowX: 'auto' }}>
          <Table aria-label="Grid fees">
            <Thead>
              <Tr>
                <Th>Applies To</Th>
                <Th>Valid From</Th>
                <Th>Fee Type</Th>
                <Th style={{ textAlign: 'right' }}>Value</Th>
                <Th>Description</Th>
                <Th />
              </Tr>
            </Thead>
            <Tbody>
              {fees.map((f) => (
                <Tr key={f.id}>
                  <Td dataLabel="Applies To"><DirectionChip value={f.appliesTo} /></Td>
                  <Td dataLabel="Valid From">{f.validFrom}</Td>
                  <Td dataLabel="Fee Type">{f.feeType}</Td>
                  <Td dataLabel="Value" style={{ textAlign: 'right' }}>{f.feeValue}</Td>
                  <Td dataLabel="Description">{f.description ?? ''}</Td>
                  <Td>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <Tooltip content="Edit">
                        <Button variant="plain" aria-label="Edit" onClick={() => openEdit(f)}>
                          <PencilAltIcon />
                        </Button>
                      </Tooltip>
                      <Tooltip content="Delete">
                        <Button variant="plain" aria-label="Delete" onClick={() => deleteMut.mutate(f.id)}>
                          <TrashIcon />
                        </Button>
                      </Tooltip>
                    </div>
                  </Td>
                </Tr>
              ))}
              {fees.length === 0 && (
                <Tr>
                  <Td colSpan={6} style={{ textAlign: 'center', color: 'var(--pf-t--global--text--color--subtle, #6a6e73)' }}>
                    No grid fees configured
                  </Td>
                </Tr>
              )}
            </Tbody>
          </Table>
        </div>
      )}
      <Modal isOpen={dialogOpen} onClose={closeDialog} variant="medium">
        <ModalHeader title={editing ? 'Edit Grid Fee' : 'Add Grid Fee'} onClose={closeDialog} />
        <ModalBody>
          <GridFeeForm initial={editing} onSave={handleSave} onCancel={closeDialog} />
        </ModalBody>
      </Modal>
    </div>
  );
}

// ---- Fixed costs tab -------------------------------------------------------

const TODAY_ISO = new Date().toISOString().slice(0, 10);

function fmtDate(isoDate) {
  if (!isoDate) return '';
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { year: 'numeric', month: '2-digit', day: '2-digit' });
}

function FixedCostsTab() {
  const { data: costs, isLoading, error } = useFixedCosts();
  const createMut = useCreateFixedCost();
  const updateMut = useUpdateFixedCost();
  const deleteMut = useDeleteFixedCost();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState({ direction: 'BOTH', validFrom: TODAY_ISO, monthlyCostEur: '', description: '' });

  const openCreate = () => {
    setEditId(null);
    setForm({ direction: 'BOTH', validFrom: TODAY_ISO, monthlyCostEur: '', description: '' });
    setDialogOpen(true);
  };

  const openEdit = (c) => {
    setEditId(c.id);
    setForm({
      direction: c.direction ?? 'BOTH',
      validFrom: c.validFrom ?? TODAY_ISO,
      monthlyCostEur: c.monthlyCostEur != null ? String(c.monthlyCostEur) : '',
      description: c.description ?? '',
    });
    setDialogOpen(true);
  };

  const handleSave = () => {
    const payload = {
      direction: form.direction,
      validFrom: form.validFrom,
      monthlyCostEur: parseFloat(form.monthlyCostEur),
      description: form.description,
    };
    if (editId != null) {
      updateMut.mutate({ id: editId, payload }, { onSuccess: () => setDialogOpen(false) });
    } else {
      createMut.mutate(payload, { onSuccess: () => setDialogOpen(false) });
    }
  };

  return (
    <div style={{ marginTop: 16 }}>
      <Button variant="primary" icon={<PlusIcon />} onClick={openCreate} style={{ marginBottom: 16 }}>
        Add Fixed Cost
      </Button>
      {isLoading && <Spinner />}
      {error && <Alert variant="danger" isInline title="Failed to load fixed costs" />}
      {costs && (
        <div style={{ overflowX: 'auto' }}>
          <Table aria-label="Fixed costs">
            <Thead>
              <Tr>
                <Th>Direction</Th>
                <Th>Valid From</Th>
                <Th style={{ textAlign: 'right' }}>Monthly Cost (EUR)</Th>
                <Th>Description</Th>
                <Th />
              </Tr>
            </Thead>
            <Tbody>
              {costs.map((c) => (
                <Tr key={c.id}>
                  <Td dataLabel="Direction"><DirectionChip value={c.direction ?? 'BOTH'} /></Td>
                  <Td dataLabel="Valid From">{fmtDate(c.validFrom)}</Td>
                  <Td dataLabel="Monthly Cost (EUR)" style={{ textAlign: 'right' }}>{fmtEur(c.monthlyCostEur)}</Td>
                  <Td dataLabel="Description">{c.description ?? ''}</Td>
                  <Td>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <Tooltip content="Edit">
                        <Button variant="plain" aria-label="Edit" onClick={() => openEdit(c)}>
                          <PencilAltIcon />
                        </Button>
                      </Tooltip>
                      <Tooltip content="Delete">
                        <Button variant="plain" aria-label="Delete" onClick={() => deleteMut.mutate(c.id)}>
                          <TrashIcon />
                        </Button>
                      </Tooltip>
                    </div>
                  </Td>
                </Tr>
              ))}
              {costs.length === 0 && (
                <Tr>
                  <Td colSpan={5} style={{ textAlign: 'center', color: 'var(--pf-t--global--text--color--subtle, #6a6e73)' }}>
                    No fixed costs configured
                  </Td>
                </Tr>
              )}
            </Tbody>
          </Table>
        </div>
      )}
      <Modal isOpen={dialogOpen} onClose={() => setDialogOpen(false)} variant="small">
        <ModalHeader
          title={editId != null ? 'Edit Fixed Cost' : 'Add Fixed Cost'}
          onClose={() => setDialogOpen(false)}
        />
        <ModalBody>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, paddingTop: 8 }}>
            <FormGroup label="Direction" fieldId="fc-direction">
              <FormSelect id="fc-direction" value={form.direction}
                onChange={(_e, v) => setForm((f) => ({ ...f, direction: v }))}>
                <FormSelectOption value="BOTH" label="Both (import &amp; export)" />
                <FormSelectOption value="IMPORT" label="Import only" />
                <FormSelectOption value="EXPORT" label="Export only" />
              </FormSelect>
            </FormGroup>
            <FormGroup label="Valid From" fieldId="fc-valid-from"
              helperText="Entry is active from this date onwards. Delete to deactivate.">
              <TextInput id="fc-valid-from" type="date" value={form.validFrom}
                onChange={(_e, v) => setForm((f) => ({ ...f, validFrom: v }))} />
            </FormGroup>
            <FormGroup label="Monthly Cost (EUR)" fieldId="fc-monthly-cost">
              <TextInput id="fc-monthly-cost" type="number" value={form.monthlyCostEur}
                onChange={(_e, v) => setForm((f) => ({ ...f, monthlyCostEur: v }))} />
            </FormGroup>
            <FormGroup label="Description" fieldId="fc-desc">
              <TextInput id="fc-desc" value={form.description}
                onChange={(_e, v) => setForm((f) => ({ ...f, description: v }))} />
            </FormGroup>
          </div>
        </ModalBody>
        <ModalFooter>
          <Button variant="primary" onClick={handleSave}>Save</Button>
          <Button variant="link" onClick={() => setDialogOpen(false)}>Cancel</Button>
        </ModalFooter>
      </Modal>
    </div>
  );
}

// ---- Config tab ------------------------------------------------------------

function ConfigTab() {
  const { data: config, isLoading } = useCostControlConfig();
  const { data: providers } = useCostControlProviders();
  const updateMut = useUpdateCostControlConfig();

  const [form, setForm] = useState(null);

  React.useEffect(() => {
    if (config && !form) {
      setForm({ ...config });
    }
  }, [config, form]);

  if (isLoading || !form) return <Spinner style={{ marginTop: 16 }} />;

  const set = (field) => (_e, v) => setForm((f) => ({ ...f, [field]: v }));
  const setNum = (field) => (_e, v) => setForm((f) => ({ ...f, [field]: Number(v) }));

  const importProviders = providers?.filter((p) => p.supportedDirections.includes('IMPORT')) ?? [];
  const exportProviders = providers?.filter((p) => p.supportedDirections.includes('EXPORT')) ?? [];

  return (
    <div style={{ marginTop: 16 }}>
      <Grid hasGutter>
        <GridItem span={12} md={6}>
          <FormGroup label="Import Price Provider" fieldId="cfg-import-provider">
            <FormSelect id="cfg-import-provider" value={form.importProviderId ?? ''} onChange={set('importProviderId')}>
              {importProviders.map((p) => (
                <FormSelectOption key={p.providerId} value={p.providerId}
                  label={`${p.displayName} (${p.providerId})`} />
              ))}
            </FormSelect>
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={6}>
          <FormGroup label="Export Price Provider" fieldId="cfg-export-provider">
            <FormSelect id="cfg-export-provider" value={form.exportProviderId ?? ''} onChange={set('exportProviderId')}>
              {exportProviders.map((p) => (
                <FormSelectOption key={p.providerId} value={p.providerId}
                  label={`${p.displayName} (${p.providerId})`} />
              ))}
            </FormSelect>
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={6}>
          <FormGroup label="Import Fetch Cron" fieldId="cfg-import-cron">
            <TextInput id="cfg-import-cron" value={form.importFetchCron ?? ''} onChange={set('importFetchCron')} />
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={6}>
          <FormGroup label="Export Fetch Cron" fieldId="cfg-export-cron">
            <TextInput id="cfg-export-cron" value={form.exportFetchCron ?? ''} onChange={set('exportFetchCron')} />
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={4}>
          <FormGroup label="Sample Interval (seconds)" fieldId="cfg-sample-interval">
            <TextInput id="cfg-sample-interval" type="number"
              value={String(form.sampleIntervalSeconds ?? '')} onChange={setNum('sampleIntervalSeconds')} />
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={4}>
          <FormGroup label="Dead-band (watts)" fieldId="cfg-deadband">
            <TextInput id="cfg-deadband" type="number"
              value={String(form.deadBandWatts ?? '')} onChange={setNum('deadBandWatts')} />
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={4}>
          <FormGroup label="Retention Hourly (days)" fieldId="cfg-retention-hourly">
            <TextInput id="cfg-retention-hourly" type="number"
              value={String(form.retentionHourlyDays ?? '')} onChange={setNum('retentionHourlyDays')} />
          </FormGroup>
        </GridItem>
        <GridItem span={12} md={4}>
          <FormGroup label="Retention Monthly (years)" fieldId="cfg-retention-monthly">
            <TextInput id="cfg-retention-monthly" type="number"
              value={String(form.retentionMonthlyYears ?? '')} onChange={setNum('retentionMonthlyYears')} />
          </FormGroup>
        </GridItem>
        <GridItem span={12}>
          <Button
            variant="primary"
            onClick={() => updateMut.mutate(form)}
            isDisabled={updateMut.isPending}
            icon={updateMut.isPending ? <Spinner size="sm" /> : null}
          >
            Save Configuration
          </Button>
        </GridItem>
      </Grid>
      {config?.updatedAt && (
        <p style={{ fontSize: '0.75rem', color: 'var(--pf-t--global--text--color--subtle, #6a6e73)', marginTop: 8 }}>
          Last updated: {formatForDisplay(config.updatedAt)}
        </p>
      )}
    </div>
  );
}

// ---- Main page -------------------------------------------------------------

const TABS = [
  { label: 'Monthly', value: 'monthly' },
  { label: 'Daily', value: 'daily' },
  { label: 'Hourly', value: 'hourly' },
  { label: 'Prices', value: 'prices' },
  { label: 'Tariff Windows', value: 'tariff-windows' },
  { label: 'Grid Fees', value: 'grid-fees' },
  { label: 'Fixed Costs', value: 'fixed-costs' },
  { label: 'Configuration', value: 'config' },
];

function CostControlPage() {
  const [tab, setTab] = useState('monthly');

  return (
    <div>
      <PageHeader
        title="Cost Control"
        subtitle="Track grid energy costs and income"
      />
      <Card>
        <CardBody>
          <Tabs activeKey={tab} onSelect={(_e, k) => setTab(k)}>
            {TABS.map((t) => (
              <Tab key={t.value} eventKey={t.value} title={<TabTitleText>{t.label}</TabTitleText>} />
            ))}
          </Tabs>
          <Divider style={{ marginBottom: 4 }} />
          {tab === 'monthly' && <MonthlySummaryTab />}
          {tab === 'daily' && <DailySummaryTab />}
          {tab === 'hourly' && <HourlyCostTab />}
          {tab === 'prices' && <EnergyPricesTab />}
          {tab === 'tariff-windows' && <TariffWindowsTab />}
          {tab === 'grid-fees' && <GridFeesTab />}
          {tab === 'fixed-costs' && <FixedCostsTab />}
          {tab === 'config' && <ConfigTab />}
        </CardBody>
      </Card>
    </div>
  );
}

export default CostControlPage;
