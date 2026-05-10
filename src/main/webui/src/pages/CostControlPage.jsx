import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import RefreshIcon from '@mui/icons-material/Refresh';
import EuroIcon from '@mui/icons-material/Euro';
import { PageHeader } from '../components/common';
import {
  useCostControlConfig,
  useUpdateCostControlConfig,
  useCostControlProviders,
  useCostControlPrices,
  useRefreshCostControlPrices,
  useMonthlyCosts,
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
  const color = normalized === 'IMPORT' ? 'warning' : normalized === 'EXPORT' ? 'success' : 'default';
  const label = normalized === 'IMPORT' ? 'Import' : normalized === 'EXPORT' ? 'Export' : 'Both';
  return <Chip label={label} size="small" color={color} />;
}

// ---- Monthly summary tab ---------------------------------------------------

function MonthlySummaryTab() {
  const { data: months, isLoading, error } = useMonthlyCosts();

  if (isLoading) return <CircularProgress sx={{ mt: 2 }} />;
  if (error) return <Alert severity="error">Failed to load monthly costs</Alert>;
  if (!months?.length) return (
    <Alert severity="info" sx={{ mt: 2 }}>
      No monthly cost data yet. Data accumulates automatically as hourly records are processed.
    </Alert>
  );

  return (
    <Table size="small" sx={{ mt: 2 }}>
      <TableHead>
        <TableRow>
          <TableCell>Month</TableCell>
          <TableCell align="right">Import kWh</TableCell>
          <TableCell align="right">Export kWh</TableCell>
          <TableCell align="right">Import Cost</TableCell>
          <TableCell align="right">Export Income</TableCell>
          <TableCell align="right">Fees</TableCell>
          <TableCell align="right">Fixed</TableCell>
          <TableCell align="right">Net Cost</TableCell>
          <TableCell align="right">Hours</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {months.map((m) => (
          <TableRow key={m.yearMonth} hover>
            <TableCell>{m.yearMonth}</TableCell>
            <TableCell align="right">{fmtKwh(m.totalImportKwh)}</TableCell>
            <TableCell align="right">{fmtKwh(m.totalExportKwh)}</TableCell>
            <TableCell align="right">{fmtEur(m.totalImportCostEur)}</TableCell>
            <TableCell align="right">{fmtEur(m.totalExportIncomeEur)}</TableCell>
            <TableCell align="right">{fmtEur(m.totalFeeEur)}</TableCell>
            <TableCell align="right">{fmtEur(m.fixedCostEur)}</TableCell>
            <TableCell align="right" sx={{ fontWeight: 700 }}>{fmtEur(m.netCostEur)}</TableCell>
            <TableCell align="right">{m.hoursCalculated}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

// ---- Hourly cost tab -------------------------------------------------------

function HourlyCostTab() {
  const now = new Date();
  const defaultFrom = new Date(now.getTime() - 24 * 60 * 60 * 1000)
    .toISOString().slice(0, 19);
  const defaultTo = now.toISOString().slice(0, 19);

  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(defaultTo);
  const [query, setQuery] = useState({ from: defaultFrom, to: defaultTo });

  const { data: rows, isLoading, error } = useHourlyCosts(query.from, query.to);

  return (
    <Box sx={{ mt: 2 }}>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
        <TextField
          label="From"
          type="datetime-local"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          size="small"
          InputLabelProps={{ shrink: true }}
        />
        <TextField
          label="To"
          type="datetime-local"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          size="small"
          InputLabelProps={{ shrink: true }}
        />
        <Button variant="outlined" onClick={() => setQuery({ from, to })}>
          Load
        </Button>
      </Stack>
      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load hourly costs</Alert>}
      {rows && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Hour</TableCell>
              <TableCell align="right">Import kWh</TableCell>
              <TableCell align="right">Export kWh</TableCell>
              <TableCell align="right">Import Price</TableCell>
              <TableCell align="right">Export Price</TableCell>
              <TableCell align="right">Import Cost</TableCell>
              <TableCell align="right">Export Income</TableCell>
              <TableCell align="right">Fees</TableCell>
              <TableCell align="right">Net</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.hourStart} hover>
                <TableCell>{r.hourStart}</TableCell>
                <TableCell align="right">{fmtKwh(r.importKwh)}</TableCell>
                <TableCell align="right">{fmtKwh(r.exportKwh)}</TableCell>
                <TableCell align="right">{fmtCt(r.priceImportCt)}</TableCell>
                <TableCell align="right">{fmtCt(r.priceExportCt)}</TableCell>
                <TableCell align="right">{fmtEur(r.importCostEur)}</TableCell>
                <TableCell align="right">{fmtEur(r.exportIncomeEur)}</TableCell>
                <TableCell align="right">{fmtEur(r.feeEur)}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>{fmtEur(r.netCostEur)}</TableCell>
              </TableRow>
            ))}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} align="center">
                  <Typography variant="body2" color="text.secondary">No data in range</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </Box>
  );
}

// ---- Energy prices tab -----------------------------------------------------

function EnergyPricesTab() {
  const { data: prices, isLoading, error } = useCostControlPrices(48);
  const refreshMutation = useRefreshCostControlPrices();

  return (
    <Box sx={{ mt: 2 }}>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={() => refreshMutation.mutate('IMPORT')}
          disabled={refreshMutation.isPending}
          size="small"
        >
          Refresh Import
        </Button>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={() => refreshMutation.mutate('EXPORT')}
          disabled={refreshMutation.isPending}
          size="small"
        >
          Refresh Export
        </Button>
      </Stack>
      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load prices</Alert>}
      {prices && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Hour Start</TableCell>
              <TableCell align="right">Import Price</TableCell>
              <TableCell>Import Source</TableCell>
              <TableCell align="right">Export Price</TableCell>
              <TableCell>Export Source</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {prices.map((p) => (
              <TableRow key={p.startTime} hover>
                <TableCell>{p.startTime}</TableCell>
                <TableCell align="right">
                  {p.priceImportCt != null ? fmtCt(p.priceImportCt) : '—'}
                </TableCell>
                <TableCell>{p.importSource ?? '—'}</TableCell>
                <TableCell align="right">
                  {p.priceExportCt != null ? fmtCt(p.priceExportCt) : '—'}
                </TableCell>
                <TableCell>{p.exportSource ?? '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Box>
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

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <Stack spacing={2} sx={{ pt: 1 }}>
      <FormControl fullWidth size="small">
        <InputLabel>Direction</InputLabel>
        <Select value={form.direction} label="Direction" onChange={set('direction')}>
          <MenuItem value="IMPORT">Import</MenuItem>
          <MenuItem value="EXPORT">Export</MenuItem>
        </Select>
      </FormControl>
      <Stack direction="row" spacing={2}>
        <TextField label="Valid From (yyyy-MM-dd)" value={form.validFrom} onChange={set('validFrom')}
          size="small" fullWidth />
        <TextField label="Valid To (yyyy-MM-dd, empty = active)" value={form.validTo}
          onChange={set('validTo')} size="small" fullWidth />
      </Stack>
      <TextField label="Days of Week (e.g. MON,TUE,WED,THU,FRI — empty = all)"
        value={form.daysOfWeek} onChange={set('daysOfWeek')} size="small" fullWidth />
      <Stack direction="row" spacing={2}>
        <TextField label="Time From (HH:mm:ss)" value={form.timeFrom} onChange={set('timeFrom')}
          size="small" fullWidth />
        <TextField label="Time To (HH:mm:ss, 00:00:00 = end-of-day)" value={form.timeTo}
          onChange={set('timeTo')} size="small" fullWidth />
      </Stack>
      <Stack direction="row" spacing={2}>
        <TextField label="Price (ct/kWh)" type="number" value={form.priceCt}
          onChange={set('priceCt')} size="small" fullWidth />
        <TextField label="Priority" type="number" value={form.priority}
          onChange={set('priority')} size="small" fullWidth />
      </Stack>
      <TextField label="Description" value={form.description} onChange={set('description')}
        size="small" fullWidth />
      <Stack direction="row" spacing={1} justifyContent="flex-end">
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={() => onSave({
          ...form,
          priceCt: parseFloat(form.priceCt),
          priority: parseInt(form.priority, 10),
        })}>
          Save
        </Button>
      </Stack>
    </Stack>
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

  return (
    <Box sx={{ mt: 2 }}>
      <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate} sx={{ mb: 2 }}>
        Add Tariff Window
      </Button>
      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load tariff windows</Alert>}
      {windows && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Direction</TableCell>
              <TableCell>Valid From</TableCell>
              <TableCell>Valid To</TableCell>
              <TableCell>Days</TableCell>
              <TableCell>Time From</TableCell>
              <TableCell>Time To</TableCell>
              <TableCell align="right">Price (ct/kWh)</TableCell>
              <TableCell align="right">Priority</TableCell>
              <TableCell>Description</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {windows.map((w) => (
              <TableRow key={w.id} hover>
                <TableCell><DirectionChip value={w.direction} /></TableCell>
                <TableCell>{w.validFrom}</TableCell>
                <TableCell>{w.validTo ?? '—'}</TableCell>
                <TableCell>{w.daysOfWeek ?? 'All'}</TableCell>
                <TableCell>{w.timeFrom}</TableCell>
                <TableCell>{w.timeTo}</TableCell>
                <TableCell align="right">{w.priceCt.toFixed(4)}</TableCell>
                <TableCell align="right">{w.priority}</TableCell>
                <TableCell>{w.description ?? ''}</TableCell>
                <TableCell>
                  <Stack direction="row">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => openEdit(w)}><EditIcon fontSize="small" /></IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" onClick={() => deleteMut.mutate(w.id)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {windows.length === 0 && (
              <TableRow>
                <TableCell colSpan={10} align="center">
                  <Typography variant="body2" color="text.secondary">
                    No tariff windows configured
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editing ? 'Edit Tariff Window' : 'Add Tariff Window'}</DialogTitle>
        <DialogContent>
          <TariffWindowForm
            initial={editing}
            onSave={handleSave}
            onCancel={() => { setDialogOpen(false); setEditing(null); }}
          />
        </DialogContent>
      </Dialog>
    </Box>
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

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <Stack spacing={2} sx={{ pt: 1 }}>
      <TextField label="Valid From (ISO, e.g. 2026-01-01T00:00:00)" value={form.validFrom}
        onChange={set('validFrom')} size="small" fullWidth />
      <FormControl fullWidth size="small">
        <InputLabel>Fee Type</InputLabel>
        <Select value={form.feeType} label="Fee Type" onChange={set('feeType')}>
          <MenuItem value="PERCENT">Percent of base cost (%)</MenuItem>
          <MenuItem value="ABSOLUTE_ENERGY">Absolute per kWh (ct/kWh)</MenuItem>
          <MenuItem value="ABSOLUTE_TIME">Absolute per month (EUR/month)</MenuItem>
        </Select>
      </FormControl>
      <TextField label="Fee Value" type="number" value={form.feeValue}
        onChange={set('feeValue')} size="small" fullWidth />
      <FormControl fullWidth size="small">
        <InputLabel>Applies To</InputLabel>
        <Select value={form.appliesTo} label="Applies To" onChange={set('appliesTo')}>
          <MenuItem value="IMPORT">Import only</MenuItem>
          <MenuItem value="EXPORT">Export only</MenuItem>
          <MenuItem value="BOTH">Both</MenuItem>
        </Select>
      </FormControl>
      <TextField label="Description" value={form.description} onChange={set('description')}
        size="small" fullWidth />
      <Stack direction="row" spacing={1} justifyContent="flex-end">
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={() => onSave({
          ...form, feeValue: parseFloat(form.feeValue),
        })}>
          Save
        </Button>
      </Stack>
    </Stack>
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

  return (
    <Box sx={{ mt: 2 }}>
      <Button variant="contained" startIcon={<AddIcon />}
        onClick={() => { setEditing(null); setDialogOpen(true); }} sx={{ mb: 2 }}>
        Add Grid Fee
      </Button>
      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load grid fees</Alert>}
      {fees && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Applies To</TableCell>
              <TableCell>Valid From</TableCell>
              <TableCell>Fee Type</TableCell>
              <TableCell align="right">Value</TableCell>
              <TableCell>Description</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {fees.map((f) => (
              <TableRow key={f.id} hover>
                <TableCell><DirectionChip value={f.appliesTo} /></TableCell>
                <TableCell>{f.validFrom}</TableCell>
                <TableCell>{f.feeType}</TableCell>
                <TableCell align="right">{f.feeValue}</TableCell>
                <TableCell>{f.description ?? ''}</TableCell>
                <TableCell>
                  <Stack direction="row">
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => { setEditing(f); setDialogOpen(true); }}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" onClick={() => deleteMut.mutate(f.id)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {fees.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  <Typography variant="body2" color="text.secondary">No grid fees configured</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editing ? 'Edit Grid Fee' : 'Add Grid Fee'}</DialogTitle>
        <DialogContent>
          <GridFeeForm
            initial={editing}
            onSave={handleSave}
            onCancel={() => { setDialogOpen(false); setEditing(null); }}
          />
        </DialogContent>
      </Dialog>
    </Box>
  );
}

// ---- Fixed costs tab -------------------------------------------------------

const TODAY_ISO = new Date().toISOString().slice(0, 10); // yyyy-MM-dd for date input

function fmtDate(isoDate) {
  // Parse yyyy-MM-dd as local date (avoid UTC offset shifting the day)
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
  const [editId, setEditId] = useState(null); // null = create mode, number = edit mode
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
    <Box sx={{ mt: 2 }}>
      <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate} sx={{ mb: 2 }}>
        Add Fixed Cost
      </Button>
      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Failed to load fixed costs</Alert>}
      {costs && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Direction</TableCell>
              <TableCell>Valid From</TableCell>
              <TableCell align="right">Monthly Cost (EUR)</TableCell>
              <TableCell>Description</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {costs.map((c) => (
              <TableRow key={c.id} hover>
                <TableCell><DirectionChip value={c.direction ?? 'BOTH'} /></TableCell>
                <TableCell>{fmtDate(c.validFrom)}</TableCell>
                <TableCell align="right">{fmtEur(c.monthlyCostEur)}</TableCell>
                <TableCell>{c.description ?? ''}</TableCell>
                <TableCell>
                  <Tooltip title="Edit">
                    <IconButton size="small" onClick={() => openEdit(c)}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton size="small"
                      onClick={() => deleteMut.mutate(c.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {costs.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography variant="body2" color="text.secondary">No fixed costs configured</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editId != null ? 'Edit Fixed Cost' : 'Add Fixed Cost'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <FormControl size="small" fullWidth>
              <InputLabel>Direction</InputLabel>
              <Select value={form.direction} label="Direction"
                onChange={(e) => setForm((f) => ({ ...f, direction: e.target.value }))}>
                <MenuItem value="BOTH">Both (import &amp; export)</MenuItem>
                <MenuItem value="IMPORT">Import only</MenuItem>
                <MenuItem value="EXPORT">Export only</MenuItem>
              </Select>
            </FormControl>
            <TextField label="Valid From" type="date" value={form.validFrom}
              onChange={(e) => setForm((f) => ({ ...f, validFrom: e.target.value }))}
              size="small" fullWidth InputLabelProps={{ shrink: true }}
              helperText="Entry is active from this date onwards. Delete to deactivate." />
            <TextField label="Monthly Cost (EUR)" type="number" value={form.monthlyCostEur}
              onChange={(e) => setForm((f) => ({ ...f, monthlyCostEur: e.target.value }))}
              size="small" fullWidth />
            <TextField label="Description" value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              size="small" fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave}>Save</Button>
        </DialogActions>
      </Dialog>
    </Box>
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

  if (isLoading || !form) return <CircularProgress sx={{ mt: 2 }} />;

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));
  const setNum = (field) => (e) => setForm((f) => ({ ...f, [field]: Number(e.target.value) }));

  const providerOptions = providers?.map((p) => (
    <MenuItem key={p.providerId} value={p.providerId}>{p.displayName} ({p.providerId})</MenuItem>
  )) ?? [];

  const importProviders = providers?.filter((p) =>
    p.supportedDirections.includes('IMPORT')
  ) ?? [];
  const exportProviders = providers?.filter((p) =>
    p.supportedDirections.includes('EXPORT')
  ) ?? [];

  return (
    <Box sx={{ mt: 2 }}>
      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <FormControl fullWidth size="small">
            <InputLabel>Import Price Provider</InputLabel>
            <Select value={form.importProviderId ?? ''} label="Import Price Provider"
              onChange={set('importProviderId')}>
              {importProviders.map((p) => (
                <MenuItem key={p.providerId} value={p.providerId}>
                  {p.displayName} ({p.providerId})
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={12} md={6}>
          <FormControl fullWidth size="small">
            <InputLabel>Export Price Provider</InputLabel>
            <Select value={form.exportProviderId ?? ''} label="Export Price Provider"
              onChange={set('exportProviderId')}>
              {exportProviders.map((p) => (
                <MenuItem key={p.providerId} value={p.providerId}>
                  {p.displayName} ({p.providerId})
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField label="Import Fetch Cron" value={form.importFetchCron ?? ''}
            onChange={set('importFetchCron')} size="small" fullWidth />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField label="Export Fetch Cron" value={form.exportFetchCron ?? ''}
            onChange={set('exportFetchCron')} size="small" fullWidth />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField label="Sample Interval (seconds)" type="number"
            value={form.sampleIntervalSeconds ?? ''} onChange={setNum('sampleIntervalSeconds')}
            size="small" fullWidth />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField label="Dead-band (watts)" type="number"
            value={form.deadBandWatts ?? ''} onChange={setNum('deadBandWatts')}
            size="small" fullWidth />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField label="Retention Hourly (days)" type="number"
            value={form.retentionHourlyDays ?? ''} onChange={setNum('retentionHourlyDays')}
            size="small" fullWidth />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField label="Retention Monthly (years)" type="number"
            value={form.retentionMonthlyYears ?? ''} onChange={setNum('retentionMonthlyYears')}
            size="small" fullWidth />
        </Grid>
        <Grid item xs={12}>
          <Button
            variant="contained"
            onClick={() => updateMut.mutate(form)}
            disabled={updateMut.isPending}
          >
            {updateMut.isPending ? <CircularProgress size={18} /> : 'Save Configuration'}
          </Button>
        </Grid>
      </Grid>
      {config?.updatedAt && (
        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          Last updated: {config.updatedAt}
        </Typography>
      )}
    </Box>
  );
}

// ---- Main page -------------------------------------------------------------

const TABS = [
  { label: 'Monthly', value: 'monthly' },
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
    <Box>
      <PageHeader
        title="Cost Control"
        subtitle="Track grid energy costs and income"
        icon={<EuroIcon />}
      />
      <Card>
        <CardContent>
          <Tabs value={tab} onChange={(_e, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
            {TABS.map((t) => (
              <Tab key={t.value} label={t.label} value={t.value} />
            ))}
          </Tabs>
          <Divider sx={{ mb: 1 }} />
          {tab === 'monthly' && <MonthlySummaryTab />}
          {tab === 'hourly' && <HourlyCostTab />}
          {tab === 'prices' && <EnergyPricesTab />}
          {tab === 'tariff-windows' && <TariffWindowsTab />}
          {tab === 'grid-fees' && <GridFeesTab />}
          {tab === 'fixed-costs' && <FixedCostsTab />}
          {tab === 'config' && <ConfigTab />}
        </CardContent>
      </Card>
    </Box>
  );
}

export default CostControlPage;
