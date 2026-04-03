# Frodo Dashboard UI - Implementation Plan

## 1. Architecture Overview

### 1.1 Technology Stack
- **React 19** with functional components and hooks
- **Material-UI (MUI) v6** for component library
- **React Router v6** for navigation
- **Zustand** for state management (simpler than Redux, perfect for this use case)
- **React Query (TanStack Query)** for API data fetching and caching
- **Axios** for HTTP client
- **Docker Compose** with Grafana + Prometheus integration

### 1.2 MVC Structure
```
src/
├── index.js                    # Entry point
├── App.js                      # Root component with router
├── theme.js                    # MUI theme configuration
│
├── models/                     # Data models & types
│   ├── Device.js              # Device type definitions
│   ├── SunSpec.js             # SunSpec model types
│   └── ConnectionStatus.js    # Enums
│
├── views/                      # Page components (Controller layer)
│   ├── DashboardView.js       # Main dashboard
│   ├── DevicesView.js         # Device management (list)
│   ├── DeviceDetailView.js    # Device detail/edit
│   ├── DeviceCreateView.js    # Create new device
│   ├── MetricsConfigView.js   # Metrics scraping configuration
│   ├── SettingsView.js        # App settings & config import/export
│   └── NotFoundView.js        # 404 page
│
├── components/                 # Reusable UI components (View layer)
│   ├── layout/
│   │   ├── MainLayout.js          # App shell with sidebar
│   │   ├── Sidebar.js             # Navigation sidebar
│   │   └── TopBar.js              # Top app bar
│   │
│   ├── dashboard/
│   │   ├── DeviceStatusCard.js    # Device online/offline status
│   │   ├── PowerMetricsCard.js    # Power generation/consumption
│   │   ├── BatteryStatusCard.js   # Battery metrics
│   │   ├── GridStatusCard.js      # Grid push/pull
│   │   └── GrafanaPanel.js        # Reusable Grafana embed
│   │
│   ├── devices/
│   │   ├── DeviceList.js          # Device table/list
│   │   ├── DeviceForm.js          # Create/edit form
│   │   ├── DeviceTestDialog.js    # Test connection dialog
│   │   ├── DeviceDiscoveryDialog.js # Auto-discovery dialog
│   │   └── DeviceIdentificationCard.js # Device info display
│   │
│   ├── metrics/
│   │   ├── MetricsConfigPanel.js      # Per-device metrics config
│   │   ├── ParameterSelector.js       # Opt-in/out parameter list
│   │   ├── ScrapingIntervalInput.js   # Interval configuration
│   │   └── MetricsStatusCard.js       # Scraping status display
│   │
│   ├── common/
│   │   ├── LoadingSpinner.js      # Loading indicator
│   │   ├── ErrorAlert.js          # Error display
│   │   ├── StatusChip.js          # Status badge
│   │   └── ConfirmDialog.js       # Confirmation dialog
│   │
│   └── grafana/
│       ├── GrafanaEmbed.js        # Grafana iframe wrapper
│       └── GrafanaPanel.js        # Reusable panel component
│
├── services/                   # Business logic & API layer (Model layer)
│   ├── api/
│   │   ├── apiClient.js           # Axios instance with interceptors
│   │   ├── deviceApi.js           # Device CRUD endpoints
│   │   ├── sunspecApi.js          # SunSpec endpoints
│   │   ├── metricsApi.js          # Metrics configuration endpoints
│   │   ├── healthApi.js           # Health check endpoints
│   │   └── infoApi.js             # App info endpoint
│   │
│   ├── configService.js       # Config import/export logic
│   ├── discoveryService.js    # Device discovery logic
│   └── grafanaService.js      # Grafana URL builder
│
├── store/                      # Zustand state management
│   ├── useDeviceStore.js      # Device state
│   ├── useAppStore.js         # Global app state
│   └── useNotificationStore.js # Toast notifications
│
├── hooks/                      # Custom React hooks
│   ├── useDevices.js          # React Query hooks for devices
│   ├── useSunSpec.js          # React Query hooks for SunSpec
│   ├── useMetricsConfig.js    # React Query hooks for metrics config
│   ├── useHealth.js           # Health check polling
│   ├── usePolling.js          # Generic polling hook
│   └── useResponsive.js       # Responsive breakpoint hook
│
└── utils/                      # Utilities
    ├── formatters.js          # Format dates, numbers, power units
    ├── validators.js          # Form validation logic
    └── constants.js           # Constants (API base URL, polling intervals)
```

---

## 2. Mobile-First Responsive Design

### 2.1 Breakpoint Strategy

Using MUI's default breakpoints with mobile-first approach:

| Breakpoint | Width | Target Devices |
|------------|-------|----------------|
| `xs` | 0-599px | Mobile phones (portrait) |
| `sm` | 600-899px | Mobile phones (landscape), small tablets |
| `md` | 900-1199px | Tablets, small laptops |
| `lg` | 1200-1535px | Desktops, laptops |
| `xl` | 1536px+ | Large screens, monitors |

### 2.2 Layout Adaptations

#### Navigation
- **Desktop (md+)**: Persistent sidebar (240px width), always visible
- **Tablet (sm-md)**: Collapsible sidebar with mini variant (icons only, 64px)
- **Mobile (xs)**: Hidden sidebar, hamburger menu in TopBar, full-screen drawer overlay

#### Dashboard Cards
- **Desktop (lg+)**: 4 columns grid
- **Tablet (md)**: 2 columns grid
- **Mobile (xs-sm)**: 1 column, full-width stacked cards

#### Device List
- **Desktop**: Full table with all columns
- **Tablet**: Table with hidden columns (description, timeouts)
- **Mobile**: Card-based list view (no table)

#### Device Form
- **Desktop**: 2-column layout for form fields
- **Mobile**: Single column, full-width inputs

#### Grafana Panels
- **Desktop**: Side-by-side panels
- **Mobile**: Full-width, stacked vertically, reduced height

### 2.3 Touch Optimizations

- Minimum touch target size: 44x44px (Apple HIG guideline)
- Increased button padding on mobile
- Swipe gestures for sidebar open/close
- Pull-to-refresh for dashboard data
- Bottom navigation bar option for mobile (future consideration)

### 2.4 Responsive Hook

```javascript
// hooks/useResponsive.js
import { useTheme, useMediaQuery } from '@mui/material';

export const useResponsive = () => {
  const theme = useTheme();
  
  return {
    isMobile: useMediaQuery(theme.breakpoints.down('sm')),
    isTablet: useMediaQuery(theme.breakpoints.between('sm', 'md')),
    isDesktop: useMediaQuery(theme.breakpoints.up('md')),
    isLargeScreen: useMediaQuery(theme.breakpoints.up('lg')),
  };
};
```

### 2.5 Component Responsive Patterns

```javascript
// Example: Responsive Grid for Dashboard Cards
<Grid container spacing={{ xs: 2, md: 3 }}>
  <Grid item xs={12} sm={6} lg={3}>
    <DeviceStatusCard />
  </Grid>
  <Grid item xs={12} sm={6} lg={3}>
    <PowerMetricsCard />
  </Grid>
  <Grid item xs={12} sm={6} lg={3}>
    <BatteryStatusCard />
  </Grid>
  <Grid item xs={12} sm={6} lg={3}>
    <GridStatusCard />
  </Grid>
</Grid>

// Example: Responsive Typography
<Typography 
  variant="h4" 
  sx={{ 
    fontSize: { xs: '1.5rem', sm: '2rem', md: '2.125rem' } 
  }}
>
  Dashboard
</Typography>

// Example: Hide element on mobile
<Box sx={{ display: { xs: 'none', md: 'block' } }}>
  <DetailedStatsPanel />
</Box>
```

---

## 3. Feature Breakdown & Implementation Order

### Phase 1: Foundation (Day 1-2)
**Goal:** Set up project structure, routing, and basic layout

#### 3.1.1 Dependencies Installation
```json
{
  "dependencies": {
    "@mui/material": "^6.1.9",
    "@mui/icons-material": "^6.1.9",
    "@emotion/react": "^11.13.3",
    "@emotion/styled": "^11.13.0",
    "react-router-dom": "^6.28.0",
    "zustand": "^5.0.2",
    "@tanstack/react-query": "^5.62.7",
    "@tanstack/react-query-devtools": "^5.62.7",
    "axios": "^1.7.9"
  }
}
```

#### 3.1.2 Core Files
- **`src/theme.js`**: MUI theme matching existing dark color scheme
- **`src/App.js`**: Router setup with routes
- **`src/components/layout/MainLayout.js`**: App shell with persistent sidebar
- **`src/components/layout/Sidebar.js`**: Navigation menu (responsive)
- **`src/components/layout/TopBar.js`**: Top app bar with hamburger menu (mobile)
- **`src/services/api/apiClient.js`**: Axios instance with base URL and error handling
- **`src/store/useNotificationStore.js`**: Global notification/toast state
- **`src/store/useAppStore.js`**: Global app state (sidebar, selected device)
- **`src/hooks/useResponsive.js`**: Responsive breakpoint detection

#### 3.1.3 Routing Structure
```
/                     -> DashboardView
/devices              -> DevicesView (list)
/devices/new          -> DeviceCreateView
/devices/:id          -> DeviceDetailView
/devices/:id/metrics  -> MetricsConfigView (per-device)
/settings             -> SettingsView
*                     -> NotFoundView (404)
```

---

### Phase 2: Device Configuration (Day 3-4)
**Goal:** Implement full CRUD for device management

#### 3.2.1 Device List View (`DevicesView`)
- **Desktop**: Table with columns: Status, Name, Host:Port, Unit ID, Last Read, Actions
- **Mobile**: Card list with essential info (name, status, host)
- Search/filter by name or host
- Add device button (navigate to `/devices/new`)
- Edit/Delete actions per row/card
- Refresh button to reload list

#### 3.2.2 Device Form (`DeviceForm` component)
**Fields:**
- Name (text, required)
- Host/IP Address (text, required, with detection button)
- Port (number, required, default: 502)
- Unit ID (number, required, default: 1, range: 0-247)
- Description (textarea, optional)
- Enabled (toggle, required, default: true)
- Connection Timeout (number, optional, 1-300 seconds)
- Request Timeout (number, optional, 1-300 seconds)

**Responsive Layout:**
- Desktop: 2-column grid for fields
- Mobile: Single column, full-width inputs

**Actions:**
- **Detect Device**: Click icon next to Host field -> opens `DeviceDiscoveryDialog`
- **Test Connection**: Button to test settings before saving
- **Save**: POST/PUT to `/api/devices`
- **Cancel**: Navigate back

#### 3.2.3 Device Detection (`DeviceDiscoveryDialog`)
- Input field for host/IP
- "Scan" button that:
  1. Attempts Modbus connection (read holding registers or device identification)
  2. Shows success/failure
  3. Pre-fills form with detected values
- **Implementation**: Use temporary connection test
- **Auto-discovery**: Future feature (read-only scan of network range)

#### 3.2.4 Device Test (`DeviceTestDialog`)
- Modal dialog that tests connection with current form values
- Progress indicator during test
- Shows result: Success (green) or Failure (red with error message)
- **Implementation**: Create temporary device or use test endpoint

#### 3.2.5 Device Detail View (`DeviceDetailView`)
- Display device metadata (name, host, port, unit ID, description, enabled)
- Show cached device identification (vendor, model, serial, firmware)
- "Refresh Identification" button -> POST `/api/devices/{id}/info/refresh`
- Edit button -> enable inline editing or navigate to edit form
- Delete button -> confirm and delete
- Tabs (desktop) or accordion (mobile):
  - **Info**: Device details + identification
  - **SunSpec**: Discovery results and model data
  - **Metrics**: Link to metrics configuration
  - **Connection**: Connection stats, health, recent errors

#### 3.2.6 API Integration (`deviceApi.js`)
```javascript
// services/api/deviceApi.js
export const deviceApi = {
  list: () => axios.get('/api/devices'),
  get: (id) => axios.get(`/api/devices/${id}`),
  create: (data) => axios.post('/api/devices', data),
  update: (id, data) => axios.put(`/api/devices/${id}`, data),
  delete: (id) => axios.delete(`/api/devices/${id}`),
  refreshInfo: (id) => axios.post(`/api/devices/${id}/info/refresh`),
  getInfo: (id, refresh = false) => axios.get(`/api/devices/${id}/info`, { params: { refresh } }),
};
```

---

### Phase 3: Server-Side Metrics Collection (Day 5-6)
**Goal:** Implement configurable metrics scraping for Prometheus/Grafana integration

This phase implements the backend service that periodically collects SunSpec data from PV modules and exposes it as Prometheus metrics. Each device can have its own scraping interval and opt-in/out parameter selection.

#### 3.3.1 Backend: Database Schema Extension

**New Entity: `FroMetricsConfig`**
```java
@Entity
@Table(name = "FroMetricsConfig")
public class MetricsConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "FroMetricsConfig_SEQ")
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "device_id", unique = true)
    private ModbusDeviceEntity device;
    
    @Column(name = "scrape_interval_seconds", nullable = false)
    private Integer scrapeIntervalSeconds = 30; // Default 30s
    
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    
    @Column(name = "last_scrape_time")
    private Instant lastScrapeTime;
    
    @Column(name = "last_scrape_status")
    @Enumerated(EnumType.STRING)
    private ScrapeStatus lastScrapeStatus; // SUCCESS, FAILED, TIMEOUT
    
    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;
}
```

**New Entity: `FroMetricsParameter`**
```java
@Entity
@Table(name = "FroMetricsParameter")
public class MetricsParameterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "FroMetricsParameter_SEQ")
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "config_id", nullable = false)
    private MetricsConfigEntity config;
    
    @Column(name = "sunspec_model_id", nullable = false)
    private Integer sunspecModelId; // e.g., 101, 103, 124
    
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName; // e.g., "W", "WH", "ChaState"
    
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    
    @Column(name = "custom_metric_name", length = 100)
    private String customMetricName; // Optional override for Prometheus metric name
}
```

**Liquibase Changelog:**
```xml
<!-- db/changelog/v1.1.0-metrics-config.xml -->
<changeSet id="create-metrics-config-table" author="frodo">
    <createTable tableName="FroMetricsConfig">
        <column name="id" type="BIGINT">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="device_id" type="BIGINT">
            <constraints nullable="false" unique="true" 
                         foreignKeyName="fk_FroMetricsConfig_device" 
                         references="FroModbusDevice(id)"/>
        </column>
        <column name="scrape_interval_seconds" type="INT" defaultValue="30">
            <constraints nullable="false"/>
        </column>
        <column name="enabled" type="BOOLEAN" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
        <column name="last_scrape_time" type="TIMESTAMP"/>
        <column name="last_scrape_status" type="VARCHAR(20)"/>
        <column name="last_error_message" type="VARCHAR(500)"/>
    </createTable>
    <createSequence sequenceName="FroMetricsConfig_SEQ" startValue="1" incrementBy="1"/>
</changeSet>

<changeSet id="create-metrics-parameter-table" author="frodo">
    <createTable tableName="FroMetricsParameter">
        <column name="id" type="BIGINT">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="config_id" type="BIGINT">
            <constraints nullable="false" 
                         foreignKeyName="fk_FroMetricsParam_config" 
                         references="FroMetricsConfig(id)"/>
        </column>
        <column name="sunspec_model_id" type="INT">
            <constraints nullable="false"/>
        </column>
        <column name="field_name" type="VARCHAR(100)">
            <constraints nullable="false"/>
        </column>
        <column name="enabled" type="BOOLEAN" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
        <column name="custom_metric_name" type="VARCHAR(100)"/>
    </createTable>
    <createSequence sequenceName="FroMetricsParameter_SEQ" startValue="1" incrementBy="1"/>
    <createIndex tableName="FroMetricsParameter" indexName="idx_FroMetricsParam_config">
        <column name="config_id"/>
    </createIndex>
</changeSet>
```

#### 3.3.2 Backend: Metrics Scraping Service

**`MetricsScrapingService.java`**
```java
@ApplicationScoped
public class MetricsScrapingService {
    private static final Logger LOG = Logger.getLogger(MetricsScrapingService.class);
    
    @Inject
    MetricsConfigRepository configRepository;
    
    @Inject
    SunSpecService sunSpecService;
    
    @Inject
    MeterRegistry meterRegistry;
    
    // Map of device ID -> scheduled task
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    // Gauge values cache (device_id -> field_name -> value)
    private final Map<Long, Map<String, AtomicDouble>> gaugeValues = new ConcurrentHashMap<>();
    
    @Inject
    Vertx vertx;
    
    void onStart(@Observes StartupEvent event) {
        // Initialize scraping for all enabled configs
        configRepository.findAllEnabled().forEach(this::scheduleDeviceScraping);
    }
    
    void onStop(@Observes ShutdownEvent event) {
        // Cancel all scheduled tasks
        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }
    
    public void scheduleDeviceScraping(MetricsConfigEntity config) {
        Long deviceId = config.getDevice().getId();
        
        // Cancel existing task if any
        cancelDeviceScraping(deviceId);
        
        if (!config.getEnabled()) {
            LOG.infof("Metrics scraping disabled for device %d", deviceId);
            return;
        }
        
        int intervalSeconds = config.getScrapeIntervalSeconds();
        
        // Register gauges for enabled parameters
        registerGauges(config);
        
        // Schedule periodic scraping
        ScheduledFuture<?> task = vertx.setPeriodic(
            intervalSeconds * 1000L,
            timerId -> scrapeDevice(config)
        );
        
        scheduledTasks.put(deviceId, task);
        LOG.infof("Scheduled metrics scraping for device %d every %d seconds", deviceId, intervalSeconds);
    }
    
    public void cancelDeviceScraping(Long deviceId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(deviceId);
        if (existing != null) {
            existing.cancel(false);
        }
    }
    
    private void registerGauges(MetricsConfigEntity config) {
        Long deviceId = config.getDevice().getId();
        String deviceName = config.getDevice().getName();
        
        gaugeValues.computeIfAbsent(deviceId, k -> new ConcurrentHashMap<>());
        
        for (MetricsParameterEntity param : config.getParameters()) {
            if (!param.getEnabled()) continue;
            
            String metricName = buildMetricName(param);
            String fieldKey = param.getSunspecModelId() + "_" + param.getFieldName();
            
            AtomicDouble gaugeValue = new AtomicDouble(Double.NaN);
            gaugeValues.get(deviceId).put(fieldKey, gaugeValue);
            
            Gauge.builder(metricName, gaugeValue, AtomicDouble::get)
                .tag("device_id", String.valueOf(deviceId))
                .tag("device_name", deviceName)
                .tag("model_id", String.valueOf(param.getSunspecModelId()))
                .tag("field", param.getFieldName())
                .description("SunSpec " + param.getFieldName() + " from model " + param.getSunspecModelId())
                .register(meterRegistry);
        }
    }
    
    private String buildMetricName(MetricsParameterEntity param) {
        if (param.getCustomMetricName() != null && !param.getCustomMetricName().isBlank()) {
            return param.getCustomMetricName();
        }
        // Default naming: frodo_sunspec_{model}_{field}
        return String.format("frodo_sunspec_%d_%s", 
            param.getSunspecModelId(), 
            param.getFieldName().toLowerCase());
    }
    
    private void scrapeDevice(MetricsConfigEntity config) {
        Long deviceId = config.getDevice().getId();
        
        // Group parameters by model ID for efficient reads
        Map<Integer, List<MetricsParameterEntity>> paramsByModel = config.getParameters().stream()
            .filter(MetricsParameterEntity::getEnabled)
            .collect(Collectors.groupingBy(MetricsParameterEntity::getSunspecModelId));
        
        for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : paramsByModel.entrySet()) {
            Integer modelId = entry.getKey();
            List<MetricsParameterEntity> params = entry.getValue();
            
            sunSpecService.readModel(deviceId, modelId)
                .subscribe().with(
                    modelData -> {
                        // Update gauge values
                        for (MetricsParameterEntity param : params) {
                            Object value = modelData.getFields().get(param.getFieldName());
                            if (value instanceof Number) {
                                String fieldKey = modelId + "_" + param.getFieldName();
                                AtomicDouble gauge = gaugeValues.get(deviceId).get(fieldKey);
                                if (gauge != null) {
                                    gauge.set(((Number) value).doubleValue());
                                }
                            }
                        }
                        updateScrapeStatus(config, ScrapeStatus.SUCCESS, null);
                    },
                    error -> {
                        LOG.warnf("Failed to scrape model %d from device %d: %s", 
                            modelId, deviceId, error.getMessage());
                        updateScrapeStatus(config, ScrapeStatus.FAILED, error.getMessage());
                    }
                );
        }
    }
    
    @Transactional
    void updateScrapeStatus(MetricsConfigEntity config, ScrapeStatus status, String errorMessage) {
        config.setLastScrapeTime(Instant.now());
        config.setLastScrapeStatus(status);
        config.setLastErrorMessage(errorMessage);
        configRepository.persist(config);
    }
}
```

#### 3.3.3 Backend: REST API Endpoints

**`MetricsConfigResource.java`**
```java
@Path("/api/devices/{deviceId}/metrics")
@Tag(name = "Metrics Configuration", description = "Configure per-device metrics scraping")
public class MetricsConfigResource {
    
    @Inject
    MetricsConfigRepository configRepository;
    
    @Inject
    MetricsScrapingService scrapingService;
    
    @Inject
    SunSpecService sunSpecService;
    
    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get metrics configuration for a device")
    public Uni<MetricsConfigResponse> getConfig(@PathParam("deviceId") Long deviceId) {
        return Uni.createFrom().item(() -> {
            MetricsConfigEntity config = configRepository.findByDeviceId(deviceId);
            if (config == null) {
                // Return default config (not yet saved)
                return MetricsConfigResponse.defaultConfig(deviceId);
            }
            return MetricsConfigResponse.from(config);
        });
    }
    
    @PUT
    @Path("/config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update metrics configuration for a device")
    @Transactional
    public Uni<MetricsConfigResponse> updateConfig(
            @PathParam("deviceId") Long deviceId,
            MetricsConfigRequest request) {
        return Uni.createFrom().item(() -> {
            MetricsConfigEntity config = configRepository.findByDeviceId(deviceId);
            if (config == null) {
                config = new MetricsConfigEntity();
                config.setDevice(deviceRepository.findById(deviceId));
            }
            
            config.setScrapeIntervalSeconds(request.scrapeIntervalSeconds());
            config.setEnabled(request.enabled());
            
            // Update parameters
            updateParameters(config, request.parameters());
            
            configRepository.persist(config);
            
            // Reschedule scraping with new config
            scrapingService.scheduleDeviceScraping(config);
            
            return MetricsConfigResponse.from(config);
        });
    }
    
    @GET
    @Path("/available-parameters")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get available SunSpec parameters for metrics collection")
    public Uni<AvailableParametersResponse> getAvailableParameters(
            @PathParam("deviceId") Long deviceId) {
        // Discover SunSpec models and return all readable numeric fields
        return sunSpecService.discoverModels(deviceId)
            .map(discovery -> {
                List<AvailableParameter> params = new ArrayList<>();
                for (ModelSummary model : discovery.getModels()) {
                    if (model.isKnown()) {
                        SunSpecModelDefinition def = SunSpecModelRegistry.getModel(model.getModelId());
                        for (SunSpecFieldDefinition field : def.getFields()) {
                            if (field.isNumeric()) {
                                params.add(new AvailableParameter(
                                    model.getModelId(),
                                    model.getName(),
                                    field.getName(),
                                    field.getLabel(),
                                    field.getUnits(),
                                    field.getDescription()
                                ));
                            }
                        }
                    }
                }
                return new AvailableParametersResponse(deviceId, params);
            });
    }
    
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get current scraping status")
    public Uni<MetricsStatusResponse> getStatus(@PathParam("deviceId") Long deviceId) {
        return Uni.createFrom().item(() -> {
            MetricsConfigEntity config = configRepository.findByDeviceId(deviceId);
            if (config == null) {
                return MetricsStatusResponse.notConfigured(deviceId);
            }
            return MetricsStatusResponse.from(config);
        });
    }
}
```

#### 3.3.4 Backend: DTOs

```java
// Request/Response DTOs
public record MetricsConfigRequest(
    @NotNull Integer scrapeIntervalSeconds,
    @NotNull Boolean enabled,
    List<ParameterConfigRequest> parameters
) {}

public record ParameterConfigRequest(
    @NotNull Integer sunspecModelId,
    @NotBlank String fieldName,
    @NotNull Boolean enabled,
    String customMetricName
) {}

public record MetricsConfigResponse(
    Long deviceId,
    Integer scrapeIntervalSeconds,
    Boolean enabled,
    List<ParameterConfigResponse> parameters,
    Instant lastScrapeTime,
    String lastScrapeStatus,
    String lastErrorMessage
) {
    public static MetricsConfigResponse defaultConfig(Long deviceId) {
        return new MetricsConfigResponse(deviceId, 30, false, List.of(), null, null, null);
    }
    
    public static MetricsConfigResponse from(MetricsConfigEntity entity) {
        return new MetricsConfigResponse(
            entity.getDevice().getId(),
            entity.getScrapeIntervalSeconds(),
            entity.getEnabled(),
            entity.getParameters().stream()
                .map(ParameterConfigResponse::from)
                .toList(),
            entity.getLastScrapeTime(),
            entity.getLastScrapeStatus() != null ? entity.getLastScrapeStatus().name() : null,
            entity.getLastErrorMessage()
        );
    }
}

public record ParameterConfigResponse(
    Long id,
    Integer sunspecModelId,
    String fieldName,
    Boolean enabled,
    String customMetricName
) {
    public static ParameterConfigResponse from(MetricsParameterEntity entity) {
        return new ParameterConfigResponse(
            entity.getId(),
            entity.getSunspecModelId(),
            entity.getFieldName(),
            entity.getEnabled(),
            entity.getCustomMetricName()
        );
    }
}

public record AvailableParameter(
    Integer modelId,
    String modelName,
    String fieldName,
    String label,
    String units,
    String description
) {}

public record AvailableParametersResponse(
    Long deviceId,
    List<AvailableParameter> parameters
) {}

public record MetricsStatusResponse(
    Long deviceId,
    Boolean configured,
    Boolean enabled,
    Integer scrapeIntervalSeconds,
    Instant lastScrapeTime,
    String lastScrapeStatus,
    String lastErrorMessage,
    Integer enabledParameterCount
) {
    public static MetricsStatusResponse notConfigured(Long deviceId) {
        return new MetricsStatusResponse(deviceId, false, false, null, null, null, null, 0);
    }
    
    public static MetricsStatusResponse from(MetricsConfigEntity entity) {
        int enabledCount = (int) entity.getParameters().stream()
            .filter(MetricsParameterEntity::getEnabled)
            .count();
        return new MetricsStatusResponse(
            entity.getDevice().getId(),
            true,
            entity.getEnabled(),
            entity.getScrapeIntervalSeconds(),
            entity.getLastScrapeTime(),
            entity.getLastScrapeStatus() != null ? entity.getLastScrapeStatus().name() : null,
            entity.getLastErrorMessage(),
            enabledCount
        );
    }
}
```

#### 3.3.5 Frontend: Metrics Configuration UI

**`views/MetricsConfigView.js`**
- Device selector (if navigated directly)
- Scraping interval input (5-300 seconds, slider or number input)
- Enable/Disable toggle for entire scraping
- Parameter list with:
  - Grouped by SunSpec model (Inverter, Storage, Status, etc.)
  - Checkbox for each parameter (opt-in/out)
  - Show field name, label, units
  - Optional custom metric name input
- Status display:
  - Last scrape time
  - Last scrape status (success/failed)
  - Error message if failed
- Save/Cancel buttons

**`components/metrics/ParameterSelector.js`**
- Accordion or grouped list by model
- Select all / Deselect all per model
- Search/filter parameters by name
- Show recommended defaults (common metrics pre-selected)

**API Integration (`metricsApi.js`)**
```javascript
export const metricsApi = {
  getConfig: (deviceId) => axios.get(`/api/devices/${deviceId}/metrics/config`),
  updateConfig: (deviceId, data) => axios.put(`/api/devices/${deviceId}/metrics/config`, data),
  getAvailableParameters: (deviceId) => axios.get(`/api/devices/${deviceId}/metrics/available-parameters`),
  getStatus: (deviceId) => axios.get(`/api/devices/${deviceId}/metrics/status`),
};
```

#### 3.3.6 Prometheus Metrics Format

The scraping service exposes metrics in Prometheus format:

```prometheus
# HELP frodo_sunspec_103_w AC Power from inverter model 103
# TYPE frodo_sunspec_103_w gauge
frodo_sunspec_103_w{device_id="1",device_name="Fronius Gen24",model_id="103",field="W"} 4523.0

# HELP frodo_sunspec_103_wh Total Energy from inverter model 103
# TYPE frodo_sunspec_103_wh gauge
frodo_sunspec_103_wh{device_id="1",device_name="Fronius Gen24",model_id="103",field="WH"} 123456789.0

# HELP frodo_sunspec_124_chastate Battery State of Charge
# TYPE frodo_sunspec_124_chastate gauge
frodo_sunspec_124_chastate{device_id="1",device_name="Fronius Gen24",model_id="124",field="ChaState"} 78.5
```

#### 3.3.7 Configuration Import/Export Extension

Extend the config export/import to include metrics configuration:

```json
{
  "version": "1.1",
  "exportDate": "2024-01-15T10:30:00Z",
  "devices": [
    {
      "name": "Fronius Gen24",
      "host": "192.168.1.100",
      "port": 502,
      "unitId": 1,
      "enabled": true,
      "metricsConfig": {
        "scrapeIntervalSeconds": 30,
        "enabled": true,
        "parameters": [
          { "modelId": 103, "fieldName": "W", "enabled": true },
          { "modelId": 103, "fieldName": "WH", "enabled": true },
          { "modelId": 124, "fieldName": "ChaState", "enabled": true }
        ]
      }
    }
  ]
}
```

#### 3.3.8 Long-Term Metrics Storage (RDBMS)

In addition to exposing metrics to Prometheus, all scraped values are persisted to the database for long-term storage, historical analysis, and backup independent of Prometheus/Grafana availability.

**New Entity: `FroMetricsData`**
```java
@Entity
@Table(name = "FroMetricsData", indexes = {
    @Index(name = "idx_FroMetricsData_device_time", columnList = "device_id, recorded_at DESC"),
    @Index(name = "idx_FroMetricsData_param_time", columnList = "parameter_id, recorded_at DESC")
})
public class MetricsDataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "FroMetricsData_SEQ")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private ModbusDeviceEntity device;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    private MetricsParameterEntity parameter;
    
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    
    @Column(name = "value_numeric")
    private Double valueNumeric; // For numeric values (most common)
    
    @Column(name = "value_string", length = 255)
    private String valueString; // For string values (rare, e.g., serial numbers)
    
    @Column(name = "sunspec_model_id", nullable = false)
    private Integer sunspecModelId;
    
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;
}
```

**Liquibase Changelog Addition:**
```xml
<!-- db/changelog/v1.1.0-metrics-config.xml (add to existing file) -->
<changeSet id="create-metrics-data-table" author="frodo">
    <createTable tableName="FroMetricsData">
        <column name="id" type="BIGINT">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="device_id" type="BIGINT">
            <constraints nullable="false" 
                         foreignKeyName="fk_FroMetricsData_device" 
                         references="FroModbusDevice(id)"/>
        </column>
        <column name="parameter_id" type="BIGINT">
            <constraints nullable="false" 
                         foreignKeyName="fk_FroMetricsData_param" 
                         references="FroMetricsParameter(id)"/>
        </column>
        <column name="recorded_at" type="TIMESTAMP">
            <constraints nullable="false"/>
        </column>
        <column name="value_numeric" type="DOUBLE"/>
        <column name="value_string" type="VARCHAR(255)"/>
        <column name="sunspec_model_id" type="INT">
            <constraints nullable="false"/>
        </column>
        <column name="field_name" type="VARCHAR(100)">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <createSequence sequenceName="FroMetricsData_SEQ" startValue="1" incrementBy="50"/>
    
    <!-- Indexes for efficient querying -->
    <createIndex tableName="FroMetricsData" indexName="idx_FroMetricsData_device_time">
        <column name="device_id"/>
        <column name="recorded_at" descending="true"/>
    </createIndex>
    <createIndex tableName="FroMetricsData" indexName="idx_FroMetricsData_param_time">
        <column name="parameter_id"/>
        <column name="recorded_at" descending="true"/>
    </createIndex>
    <createIndex tableName="FroMetricsData" indexName="idx_FroMetricsData_time">
        <column name="recorded_at" descending="true"/>
    </createIndex>
</changeSet>
```

**MetricsConfig Entity Extension (Retention Settings):**
```java
// Add to MetricsConfigEntity
@Column(name = "retention_days", nullable = false)
private Integer retentionDays = 365; // Default 1 year retention

@Column(name = "store_to_database", nullable = false)
private Boolean storeToDatabase = true; // Enable/disable DB storage
```

**MetricsDataRepository:**
```java
@ApplicationScoped
public class MetricsDataRepository implements PanacheRepository<MetricsDataEntity> {
    
    public List<MetricsDataEntity> findByDeviceAndTimeRange(
            Long deviceId, Instant from, Instant to, int limit) {
        return find("device.id = ?1 and recordedAt >= ?2 and recordedAt <= ?3 order by recordedAt desc",
            deviceId, from, to)
            .page(0, limit)
            .list();
    }
    
    public List<MetricsDataEntity> findByParameterAndTimeRange(
            Long parameterId, Instant from, Instant to, int limit) {
        return find("parameter.id = ?1 and recordedAt >= ?2 and recordedAt <= ?3 order by recordedAt desc",
            parameterId, from, to)
            .page(0, limit)
            .list();
    }
    
    public List<MetricsDataEntity> findLatestByDevice(Long deviceId, int limit) {
        return find("device.id = ?1 order by recordedAt desc", deviceId)
            .page(0, limit)
            .list();
    }
    
    // Aggregation query for time-bucketed data (e.g., hourly averages)
    @Transactional
    public List<Object[]> findAggregatedByDeviceAndField(
            Long deviceId, Integer modelId, String fieldName,
            Instant from, Instant to, String aggregation) {
        // Note: Firebird SQL syntax for date truncation
        String sql = """
            SELECT 
                CAST(recorded_at AS DATE) as bucket,
                %s(value_numeric) as agg_value,
                COUNT(*) as sample_count
            FROM FroMetricsData 
            WHERE device_id = ?1 
              AND sunspec_model_id = ?2 
              AND field_name = ?3
              AND recorded_at >= ?4 
              AND recorded_at <= ?5
              AND value_numeric IS NOT NULL
            GROUP BY CAST(recorded_at AS DATE)
            ORDER BY bucket DESC
            """.formatted(aggregation); // AVG, MIN, MAX, SUM
        return getEntityManager()
            .createNativeQuery(sql)
            .setParameter(1, deviceId)
            .setParameter(2, modelId)
            .setParameter(3, fieldName)
            .setParameter(4, from)
            .setParameter(5, to)
            .getResultList();
    }
    
    // Cleanup old data based on retention policy
    @Transactional
    public int deleteOlderThan(Long deviceId, Instant cutoffTime) {
        return delete("device.id = ?1 and recordedAt < ?2", deviceId, cutoffTime);
    }
}
```

**Updated MetricsScrapingService (with DB persistence):**
```java
@ApplicationScoped
public class MetricsScrapingService {
    // ... existing fields ...
    
    @Inject
    MetricsDataRepository dataRepository;
    
    private void scrapeDevice(MetricsConfigEntity config) {
        Long deviceId = config.getDevice().getId();
        Instant scrapeTime = Instant.now();
        
        // Group parameters by model ID for efficient reads
        Map<Integer, List<MetricsParameterEntity>> paramsByModel = config.getParameters().stream()
            .filter(MetricsParameterEntity::getEnabled)
            .collect(Collectors.groupingBy(MetricsParameterEntity::getSunspecModelId));
        
        for (Map.Entry<Integer, List<MetricsParameterEntity>> entry : paramsByModel.entrySet()) {
            Integer modelId = entry.getKey();
            List<MetricsParameterEntity> params = entry.getValue();
            
            sunSpecService.readModel(deviceId, modelId)
                .subscribe().with(
                    modelData -> {
                        List<MetricsDataEntity> dataPoints = new ArrayList<>();
                        
                        for (MetricsParameterEntity param : params) {
                            Object value = modelData.getFields().get(param.getFieldName());
                            
                            // Update Prometheus gauge
                            if (value instanceof Number) {
                                String fieldKey = modelId + "_" + param.getFieldName();
                                AtomicDouble gauge = gaugeValues.get(deviceId).get(fieldKey);
                                if (gauge != null) {
                                    gauge.set(((Number) value).doubleValue());
                                }
                            }
                            
                            // Store to database if enabled
                            if (config.getStoreToDatabase() && value != null) {
                                MetricsDataEntity dataPoint = new MetricsDataEntity();
                                dataPoint.setDevice(config.getDevice());
                                dataPoint.setParameter(param);
                                dataPoint.setRecordedAt(scrapeTime);
                                dataPoint.setSunspecModelId(modelId);
                                dataPoint.setFieldName(param.getFieldName());
                                
                                if (value instanceof Number) {
                                    dataPoint.setValueNumeric(((Number) value).doubleValue());
                                } else {
                                    dataPoint.setValueString(String.valueOf(value));
                                }
                                
                                dataPoints.add(dataPoint);
                            }
                        }
                        
                        // Batch persist data points
                        if (!dataPoints.isEmpty()) {
                            persistDataPoints(dataPoints);
                        }
                        
                        updateScrapeStatus(config, ScrapeStatus.SUCCESS, null);
                    },
                    error -> {
                        LOG.warnf("Failed to scrape model %d from device %d: %s", 
                            modelId, deviceId, error.getMessage());
                        updateScrapeStatus(config, ScrapeStatus.FAILED, error.getMessage());
                    }
                );
        }
    }
    
    @Transactional
    void persistDataPoints(List<MetricsDataEntity> dataPoints) {
        for (MetricsDataEntity point : dataPoints) {
            dataRepository.persist(point);
        }
    }
}
```

**Data Retention Cleanup Service:**
```java
@ApplicationScoped
public class MetricsRetentionService {
    private static final Logger LOG = Logger.getLogger(MetricsRetentionService.class);
    
    @Inject
    MetricsConfigRepository configRepository;
    
    @Inject
    MetricsDataRepository dataRepository;
    
    // Run daily at 2:00 AM
    @Scheduled(cron = "0 0 2 * * ?")
    void cleanupOldData() {
        LOG.info("Starting metrics data retention cleanup");
        
        List<MetricsConfigEntity> configs = configRepository.listAll();
        int totalDeleted = 0;
        
        for (MetricsConfigEntity config : configs) {
            if (config.getRetentionDays() != null && config.getRetentionDays() > 0) {
                Instant cutoff = Instant.now().minus(config.getRetentionDays(), ChronoUnit.DAYS);
                int deleted = dataRepository.deleteOlderThan(config.getDevice().getId(), cutoff);
                totalDeleted += deleted;
                
                if (deleted > 0) {
                    LOG.infof("Deleted %d old metrics records for device %s (retention: %d days)",
                        deleted, config.getDevice().getName(), config.getRetentionDays());
                }
            }
        }
        
        LOG.infof("Metrics retention cleanup completed. Total deleted: %d records", totalDeleted);
    }
}
```

**REST API Endpoints for Historical Data:**
```java
// Add to MetricsConfigResource.java

@GET
@Path("/data")
@Produces(MediaType.APPLICATION_JSON)
@Operation(summary = "Get historical metrics data for a device")
public Uni<MetricsDataResponse> getHistoricalData(
        @PathParam("deviceId") Long deviceId,
        @QueryParam("from") String fromStr,      // ISO-8601 timestamp
        @QueryParam("to") String toStr,          // ISO-8601 timestamp
        @QueryParam("modelId") Integer modelId,  // Optional filter
        @QueryParam("field") String fieldName,   // Optional filter
        @QueryParam("limit") @DefaultValue("1000") int limit) {
    
    Instant from = fromStr != null ? Instant.parse(fromStr) : Instant.now().minus(24, ChronoUnit.HOURS);
    Instant to = toStr != null ? Instant.parse(toStr) : Instant.now();
    
    return Uni.createFrom().item(() -> {
        List<MetricsDataEntity> data = dataRepository.findByDeviceAndTimeRange(deviceId, from, to, limit);
        
        // Filter by model/field if specified
        if (modelId != null) {
            data = data.stream().filter(d -> d.getSunspecModelId().equals(modelId)).toList();
        }
        if (fieldName != null) {
            data = data.stream().filter(d -> d.getFieldName().equals(fieldName)).toList();
        }
        
        return MetricsDataResponse.from(deviceId, data, from, to);
    });
}

@GET
@Path("/data/aggregated")
@Produces(MediaType.APPLICATION_JSON)
@Operation(summary = "Get aggregated metrics data (daily averages, etc.)")
public Uni<AggregatedMetricsResponse> getAggregatedData(
        @PathParam("deviceId") Long deviceId,
        @QueryParam("modelId") @NotNull Integer modelId,
        @QueryParam("field") @NotBlank String fieldName,
        @QueryParam("from") String fromStr,
        @QueryParam("to") String toStr,
        @QueryParam("aggregation") @DefaultValue("AVG") String aggregation) {
    
    Instant from = fromStr != null ? Instant.parse(fromStr) : Instant.now().minus(30, ChronoUnit.DAYS);
    Instant to = toStr != null ? Instant.parse(toStr) : Instant.now();
    
    return Uni.createFrom().item(() -> {
        List<Object[]> results = dataRepository.findAggregatedByDeviceAndField(
            deviceId, modelId, fieldName, from, to, aggregation.toUpperCase());
        return AggregatedMetricsResponse.from(deviceId, modelId, fieldName, aggregation, results);
    });
}

@GET
@Path("/data/latest")
@Produces(MediaType.APPLICATION_JSON)
@Operation(summary = "Get latest metrics values for a device")
public Uni<LatestMetricsResponse> getLatestData(
        @PathParam("deviceId") Long deviceId,
        @QueryParam("limit") @DefaultValue("100") int limit) {
    
    return Uni.createFrom().item(() -> {
        List<MetricsDataEntity> data = dataRepository.findLatestByDevice(deviceId, limit);
        return LatestMetricsResponse.from(deviceId, data);
    });
}
```

**Response DTOs for Historical Data:**
```java
public record MetricsDataResponse(
    Long deviceId,
    Instant from,
    Instant to,
    int count,
    List<MetricsDataPoint> data
) {
    public static MetricsDataResponse from(Long deviceId, List<MetricsDataEntity> entities, Instant from, Instant to) {
        List<MetricsDataPoint> points = entities.stream()
            .map(e -> new MetricsDataPoint(
                e.getRecordedAt(),
                e.getSunspecModelId(),
                e.getFieldName(),
                e.getValueNumeric(),
                e.getValueString()
            ))
            .toList();
        return new MetricsDataResponse(deviceId, from, to, points.size(), points);
    }
}

public record MetricsDataPoint(
    Instant timestamp,
    Integer modelId,
    String fieldName,
    Double numericValue,
    String stringValue
) {}

public record AggregatedMetricsResponse(
    Long deviceId,
    Integer modelId,
    String fieldName,
    String aggregation,
    List<AggregatedDataPoint> data
) {
    public static AggregatedMetricsResponse from(Long deviceId, Integer modelId, String fieldName, 
            String aggregation, List<Object[]> results) {
        List<AggregatedDataPoint> points = results.stream()
            .map(r -> new AggregatedDataPoint(
                ((java.sql.Date) r[0]).toLocalDate(),
                ((Number) r[1]).doubleValue(),
                ((Number) r[2]).intValue()
            ))
            .toList();
        return new AggregatedMetricsResponse(deviceId, modelId, fieldName, aggregation, points);
    }
}

public record AggregatedDataPoint(
    java.time.LocalDate date,
    Double value,
    Integer sampleCount
) {}

public record LatestMetricsResponse(
    Long deviceId,
    Instant timestamp,
    Map<String, Object> values  // fieldKey -> value
) {
    public static LatestMetricsResponse from(Long deviceId, List<MetricsDataEntity> data) {
        Map<String, Object> values = new LinkedHashMap<>();
        Instant latest = null;
        
        // Group by field and get latest value for each
        Map<String, MetricsDataEntity> latestByField = data.stream()
            .collect(Collectors.toMap(
                e -> e.getSunspecModelId() + "_" + e.getFieldName(),
                e -> e,
                (e1, e2) -> e1.getRecordedAt().isAfter(e2.getRecordedAt()) ? e1 : e2
            ));
        
        for (MetricsDataEntity e : latestByField.values()) {
            String key = e.getSunspecModelId() + "_" + e.getFieldName();
            values.put(key, e.getValueNumeric() != null ? e.getValueNumeric() : e.getValueString());
            if (latest == null || e.getRecordedAt().isAfter(latest)) {
                latest = e.getRecordedAt();
            }
        }
        
        return new LatestMetricsResponse(deviceId, latest, values);
    }
}
```

**Frontend API Extension (`metricsApi.js`):**
```javascript
export const metricsApi = {
  // ... existing methods ...
  
  // Historical data endpoints
  getHistoricalData: (deviceId, params) => 
    axios.get(`/api/devices/${deviceId}/metrics/data`, { params }),
  
  getAggregatedData: (deviceId, params) => 
    axios.get(`/api/devices/${deviceId}/metrics/data/aggregated`, { params }),
  
  getLatestData: (deviceId, limit = 100) => 
    axios.get(`/api/devices/${deviceId}/metrics/data/latest`, { params: { limit } }),
};
```

**Frontend: MetricsConfigView Extension**
Add to the metrics configuration UI:
- **Storage Settings Section:**
  - Enable/disable database storage toggle
  - Retention period input (days, default 365)
  - Show current storage usage (record count, estimated size)
- **Data Management:**
  - Export historical data button (CSV download)
  - Manual cleanup button with confirmation
  - Storage statistics display

**Storage Considerations:**
- With 30-second scraping and 10 parameters per device:
  - ~2,880 records/device/day
  - ~1M records/device/year
- Indexes on (device_id, recorded_at) and (parameter_id, recorded_at) for query performance
- Sequence increment of 50 for batch insert optimization
- Consider partitioning by month for very large deployments (future enhancement)

---

### Phase 4: Dashboard View (Day 7-8)
**Goal:** Display real-time device metrics and status

#### 3.4.1 Dashboard Layout (`DashboardView`)
- Responsive grid layout with cards (MUI Grid)
- Auto-refresh every 5-10 seconds for selected device
- Device selector dropdown (if multiple devices)
- Pull-to-refresh on mobile

#### 3.4.2 Dashboard Cards

**A. Device Status Card (`DeviceStatusCard`)**
- Connection status indicator (green/yellow/red)
- Online/offline badge
- Last successful read timestamp
- Device name, model, serial
- Compact mode for mobile

**B. Power Metrics Card (`PowerMetricsCard`)**
- Data from `/api/devices/{id}/sunspec/inverter` (model 101-103, 111-113)
- Fields to display:
  - **AC Power** (W field: `W`, `WphA`, `WphB`, `WphC`)
  - **AC Voltage** (V field: `PhVphA`, `PhVphB`, `PhVphC`)
  - **AC Current** (A field: `AphA`, `AphB`, `AphC`)
  - **Frequency** (Hz field: `Hz`)
  - **Energy Total** (Wh field: `WH`)
- Show numeric values with units
- Color-coded indicators (green for generating, gray for idle)
- Mobile: Show only primary metrics (AC Power, Energy Total)

**C. Battery Status Card (`BatteryStatusCard`)**
- Data from `/api/devices/{id}/sunspec/storage` (model 124)
- Fields:
  - **State of Charge** (% field: `ChaState`)
  - **Battery Voltage** (V)
  - **Battery Current** (A, + = charging, - = discharging)
  - **Battery Power** (W)
  - **Battery Status** (enum: idle, charging, discharging)
- Visual battery icon with fill level
- Mobile: Simplified view with SoC percentage and status icon

**D. Grid Status Card (`GridStatusCard`)**
- Data from `/api/devices/{id}/sunspec/inverter` or `/status` (model 122)
- Fields:
  - **Grid Power** (W, + = export/push to grid, - = import/pull from grid)
  - **Grid Status**: Connected, Disconnected, Exporting, Importing
  - **Total Grid Export** (Wh)
  - **Total Grid Import** (Wh)
- Arrow icons indicating direction (up arrow export, down arrow import)
- Color-coded: green for export, orange for import

#### 3.4.3 Polling & Data Refresh
- Use `usePolling` hook with React Query
- Poll inverter data every 5-10 seconds
- Show "Last updated: X seconds ago"
- Manual refresh button
- Pull-to-refresh gesture on mobile (future enhancement)

#### 3.4.4 Error Handling
- Show `ErrorAlert` if device unreachable (503)
- Show stale data warning if last update > 60 seconds
- Retry mechanism with exponential backoff

---

### Phase 5: Grafana Integration (Day 9-10)
**Goal:** Embed Grafana dashboards and panels

#### 3.5.1 Grafana Configuration
**Development Environment:**
```yaml
# docker-compose.yml additions
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
  
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ALLOW_EMBEDDING=true
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
    volumes:
      - grafana-data:/var/lib/grafana

volumes:
  grafana-data:
```

**Prometheus scrape config:**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'frodo'
    static_configs:
      - targets: ['host.docker.internal:8080']
    metrics_path: '/q/metrics'
    scrape_interval: 5s
```

#### 3.5.2 Grafana Embed Component
**`components/grafana/GrafanaEmbed.js`:**
- Accept props: `dashboardUid`, `panelId`, `from`, `to`, `refresh`, `theme`
- Build iframe URL: `http://localhost:3000/d-solo/{uid}/{slug}?panelId={id}&from={from}&to={to}&theme={theme}`
- Handle responsive sizing (aspect ratio preservation)
- Error handling for failed loads
- Loading skeleton while iframe loads

**`components/grafana/GrafanaPanel.js`:**
- Wrapper with MUI Card styling
- Loading skeleton
- Full-screen toggle button
- Refresh controls
- Responsive height (shorter on mobile)

#### 3.5.3 Grafana Service (`grafanaService.js`)
```javascript
export const grafanaService = {
  buildPanelUrl: (config) => {
    const { 
      dashboardUid, 
      panelId, 
      from = 'now-1h', 
      to = 'now', 
      refresh = '5s', 
      theme = 'dark' 
    } = config;
    return `${GRAFANA_BASE_URL}/d-solo/${dashboardUid}?panelId=${panelId}&from=${from}&to=${to}&refresh=${refresh}&theme=${theme}`;
  },
  
  buildDashboardUrl: (uid) => {
    return `${GRAFANA_BASE_URL}/d/${uid}`;
  },
};
```

#### 3.5.4 Dashboard with Grafana Panels
Add Grafana panels to `DashboardView`:
- **Power Generation Chart**: Line chart of AC power over time
- **Battery State Chart**: Area chart of battery SoC over time
- **Grid Flow Chart**: Bar/line chart of grid import/export
- **System Overview**: Multi-stat panel with key metrics

Responsive behavior:
- Desktop: 2x2 grid of panels
- Tablet: 2 columns
- Mobile: Full-width stacked, reduced height (200px vs 300px)

---

### Phase 6: Settings & Configuration Management (Day 11)
**Goal:** Config import/export for disaster recovery

#### 3.6.1 Settings View (`SettingsView`)
**Sections:**
1. **Application Info**: Display from `/api/info`
2. **Configuration Export/Import**:
   - **Export**: Button to download JSON with all devices + metrics config
   - **Import**: File picker to upload JSON and restore devices
3. **Health Status**: Display from `/q/health/ready`
4. **Connection Pool Stats**: Display Modbus connection metrics
5. **Grafana Settings** (future): Configure Grafana URL, dashboard UIDs

#### 3.6.2 Config Service (`configService.js`)
```javascript
export const configService = {
  exportConfig: async () => {
    const devices = await deviceApi.list();
    
    // Fetch metrics config for each device
    const devicesWithMetrics = await Promise.all(
      devices.data.devices.map(async (device) => {
        const metricsConfig = await metricsApi.getConfig(device.id);
        return {
          name: device.name,
          host: device.host,
          port: device.port,
          unitId: device.unitId,
          enabled: device.enabled,
          description: device.description,
          metricsConfig: metricsConfig.data.enabled ? {
            scrapeIntervalSeconds: metricsConfig.data.scrapeIntervalSeconds,
            enabled: metricsConfig.data.enabled,
            parameters: metricsConfig.data.parameters,
          } : null,
        };
      })
    );
    
    const config = {
      version: '1.1',
      exportDate: new Date().toISOString(),
      devices: devicesWithMetrics,
    };
    
    const blob = new Blob([JSON.stringify(config, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `frodo-config-${new Date().toISOString().split('T')[0]}.json`;
    link.click();
    URL.revokeObjectURL(url);
  },
  
  importConfig: async (file) => {
    const content = await file.text();
    const config = JSON.parse(content);
    
    // Validate config structure
    if (!config.version || !Array.isArray(config.devices)) {
      throw new Error('Invalid configuration file format');
    }
    
    // Import devices one by one
    const results = [];
    for (const device of config.devices) {
      try {
        // Create device
        const created = await deviceApi.create({
          name: device.name,
          host: device.host,
          port: device.port,
          unitId: device.unitId,
          enabled: device.enabled,
          description: device.description,
        });
        
        // Import metrics config if present
        if (device.metricsConfig) {
          await metricsApi.updateConfig(created.data.id, device.metricsConfig);
        }
        
        results.push({ device: device.name, success: true });
      } catch (error) {
        results.push({ device: device.name, success: false, error: error.message });
      }
    }
    return results;
  },
};
```

---

### Phase 7: Polish & UX (Day 12)
**Goal:** Refinements, loading states, error handling

#### 3.7.1 Common Components
- **LoadingSpinner**: Centered spinner with MUI CircularProgress
- **ErrorAlert**: MUI Alert with error message and retry button
- **StatusChip**: Colored chip for connection status (green/yellow/red)
- **ConfirmDialog**: Reusable confirmation modal for delete actions
- **EmptyState**: Placeholder for empty lists/data

#### 3.7.2 Notifications
- Toast notifications for success/error actions (using Zustand + MUI Snackbar)
- Example: "Device created successfully", "Connection test failed"
- Auto-dismiss after 5 seconds
- Manual dismiss button

#### 3.7.3 Responsive Design Checklist
- [ ] Sidebar: Drawer on mobile, persistent on desktop
- [ ] TopBar: Hamburger menu on mobile
- [ ] Dashboard: Single column on mobile, grid on desktop
- [ ] Device list: Cards on mobile, table on desktop
- [ ] Forms: Full-width on mobile, 2-column on desktop
- [ ] Grafana panels: Reduced height on mobile
- [ ] Touch targets: Minimum 44x44px
- [ ] Typography: Responsive font sizes

#### 3.7.4 Accessibility
- ARIA labels on interactive elements
- Keyboard navigation support
- Focus management in dialogs
- Skip to main content link
- Color contrast compliance (WCAG AA)
- Screen reader announcements for notifications

---

## 4. State Management Strategy

### 4.1 Zustand Stores

**`store/useAppStore.js`** - Global app state:
```javascript
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export const useAppStore = create(
  persist(
    (set) => ({
      sidebarOpen: true,
      sidebarCollapsed: false, // mini variant for tablet
      selectedDeviceId: null,
      
      toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
      toggleSidebarCollapsed: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
      setSelectedDevice: (id) => set({ selectedDeviceId: id }),
    }),
    {
      name: 'frodo-app-storage',
      partialize: (state) => ({ 
        sidebarCollapsed: state.sidebarCollapsed,
        selectedDeviceId: state.selectedDeviceId,
      }),
    }
  )
);
```

**`store/useNotificationStore.js`** - Notifications:
```javascript
import { create } from 'zustand';

export const useNotificationStore = create((set) => ({
  notifications: [],
  
  addNotification: (message, severity = 'info') => set((state) => ({
    notifications: [
      ...state.notifications, 
      { id: Date.now(), message, severity, open: true }
    ],
  })),
  
  removeNotification: (id) => set((state) => ({
    notifications: state.notifications.filter(n => n.id !== id),
  })),
  
  closeNotification: (id) => set((state) => ({
    notifications: state.notifications.map(n => 
      n.id === id ? { ...n, open: false } : n
    ),
  })),
  
  // Convenience methods
  success: (message) => set((state) => ({
    notifications: [...state.notifications, { id: Date.now(), message, severity: 'success', open: true }],
  })),
  
  error: (message) => set((state) => ({
    notifications: [...state.notifications, { id: Date.now(), message, severity: 'error', open: true }],
  })),
}));
```

### 4.2 React Query Hooks

**`hooks/useDevices.js`**:
```javascript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { deviceApi } from '../services/api/deviceApi';

export const useDevices = () => {
  return useQuery({
    queryKey: ['devices'],
    queryFn: async () => {
      const response = await deviceApi.list();
      return response.data;
    },
    refetchInterval: 30000, // 30s
  });
};

export const useDevice = (id) => {
  return useQuery({
    queryKey: ['device', id],
    queryFn: async () => {
      const response = await deviceApi.get(id);
      return response.data;
    },
    enabled: !!id,
  });
};

export const useCreateDevice = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data) => deviceApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devices'] });
    },
  });
};

export const useUpdateDevice = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => deviceApi.update(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['devices'] });
      queryClient.invalidateQueries({ queryKey: ['device', id] });
    },
  });
};

export const useDeleteDevice = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => deviceApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devices'] });
    },
  });
};

export const useRefreshDeviceInfo = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => deviceApi.refreshInfo(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['device', id] });
    },
  });
};
```

**`hooks/useMetricsConfig.js`**:
```javascript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metricsApi } from '../services/api/metricsApi';

export const useMetricsConfig = (deviceId) => {
  return useQuery({
    queryKey: ['metricsConfig', deviceId],
    queryFn: async () => {
      const response = await metricsApi.getConfig(deviceId);
      return response.data;
    },
    enabled: !!deviceId,
  });
};

export const useAvailableParameters = (deviceId) => {
  return useQuery({
    queryKey: ['availableParameters', deviceId],
    queryFn: async () => {
      const response = await metricsApi.getAvailableParameters(deviceId);
      return response.data;
    },
    enabled: !!deviceId,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useUpdateMetricsConfig = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ deviceId, data }) => metricsApi.updateConfig(deviceId, data),
    onSuccess: (_, { deviceId }) => {
      queryClient.invalidateQueries({ queryKey: ['metricsConfig', deviceId] });
    },
  });
};

export const useMetricsStatus = (deviceId) => {
  return useQuery({
    queryKey: ['metricsStatus', deviceId],
    queryFn: async () => {
      const response = await metricsApi.getStatus(deviceId);
      return response.data;
    },
    enabled: !!deviceId,
    refetchInterval: 10000, // 10s
  });
};
```

**`hooks/useSunSpec.js`**:
```javascript
import { useQuery } from '@tanstack/react-query';
import { sunspecApi } from '../services/api/sunspecApi';

export const useSunSpecDiscovery = (deviceId, options = {}) => {
  return useQuery({
    queryKey: ['sunspec', 'discovery', deviceId],
    queryFn: async () => {
      const response = await sunspecApi.getDiscovery(deviceId);
      return response.data;
    },
    enabled: !!deviceId,
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
};

export const useSunSpecInverter = (deviceId, enabled = true) => {
  return useQuery({
    queryKey: ['sunspec', 'inverter', deviceId],
    queryFn: async () => {
      const response = await sunspecApi.getInverter(deviceId);
      return response.data;
    },
    refetchInterval: 5000, // 5s for real-time data
    enabled: enabled && !!deviceId,
  });
};

export const useSunSpecStorage = (deviceId, enabled = true) => {
  return useQuery({
    queryKey: ['sunspec', 'storage', deviceId],
    queryFn: async () => {
      const response = await sunspecApi.getStorage(deviceId);
      return response.data;
    },
    refetchInterval: 10000, // 10s
    enabled: enabled && !!deviceId,
  });
};

export const useSunSpecStatus = (deviceId, enabled = true) => {
  return useQuery({
    queryKey: ['sunspec', 'status', deviceId],
    queryFn: async () => {
      const response = await sunspecApi.getStatus(deviceId);
      return response.data;
    },
    refetchInterval: 10000, // 10s
    enabled: enabled && !!deviceId,
  });
};
```

---

## 5. API Error Handling Strategy

### 5.1 Axios Interceptor
```javascript
// services/api/apiClient.js
import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const { status, data } = error.response || {};
    
    // Build standardized error object
    const apiError = {
      status: status || 0,
      message: data?.message || 'Network error',
      error: data?.error || 'Unknown',
      path: data?.path || error.config?.url,
    };
    
    if (status === 404) {
      apiError.message = data?.message || 'Resource not found';
    } else if (status === 503) {
      apiError.message = data?.message || 'Device unreachable. Check connection.';
    } else if (status === 400) {
      apiError.message = data?.message || 'Invalid request';
    } else if (status >= 500) {
      apiError.message = data?.message || 'Server error. Please try again.';
    } else if (!status) {
      apiError.message = 'Network error. Check your connection.';
    }
    
    return Promise.reject(apiError);
  }
);

export default apiClient;
```

### 5.2 React Query Error Handling
- Use `onError` callbacks in mutations
- Display errors via notification store
- Retry logic for transient failures (503)

```javascript
// Example mutation with error handling
const createDevice = useCreateDevice();

const handleSubmit = async (data) => {
  try {
    await createDevice.mutateAsync(data);
    notifications.success('Device created successfully');
    navigate('/devices');
  } catch (error) {
    notifications.error(error.message);
  }
};
```

---

## 6. Styling & Theme

### 6.1 MUI Theme Configuration
```javascript
// src/theme.js
import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#e94560',
      light: '#ff6b8a',
      dark: '#b31744',
    },
    secondary: {
      main: '#4fc3f7',
      light: '#8bf6ff',
      dark: '#0093c4',
    },
    background: {
      default: '#1a1a2e',
      paper: '#16213e',
    },
    text: {
      primary: '#e0e0e0',
      secondary: '#a0a8b8',
    },
    success: {
      main: '#4caf50',
      light: '#80e27e',
      dark: '#087f23',
    },
    error: {
      main: '#e94560',
      light: '#ff6b8a',
      dark: '#b31744',
    },
    warning: {
      main: '#ff9800',
      light: '#ffc947',
      dark: '#c66900',
    },
    info: {
      main: '#4fc3f7',
    },
    divider: '#0f3460',
  },
  typography: {
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      '"Helvetica Neue"',
      'Arial',
      'sans-serif',
    ].join(','),
    h1: {
      fontSize: '2.5rem',
      fontWeight: 600,
    },
    h2: {
      fontSize: '2rem',
      fontWeight: 600,
    },
    h3: {
      fontSize: '1.75rem',
      fontWeight: 600,
    },
    h4: {
      fontSize: '1.5rem',
      fontWeight: 600,
    },
    h5: {
      fontSize: '1.25rem',
      fontWeight: 600,
    },
    h6: {
      fontSize: '1rem',
      fontWeight: 600,
    },
  },
  shape: {
    borderRadius: 12,
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          boxShadow: '0 4px 16px rgba(0, 0, 0, 0.3)',
          border: '1px solid #0f3460',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          textTransform: 'none',
          fontWeight: 500,
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            borderRadius: 8,
          },
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: '#16213e',
          borderRight: '1px solid #0f3460',
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: '#16213e',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.4)',
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottom: '1px solid #0f3460',
        },
      },
    },
  },
});
```

### 6.2 Consistent Spacing
- Use MUI `sx` prop or styled components
- Standard spacing: `theme.spacing(1)` = 8px
- Card padding: `theme.spacing(2)` (16px) on mobile, `theme.spacing(3)` (24px) on desktop

---

## 7. Backend Requirements

### 7.1 Missing Endpoint: Device Connection Test

The backend currently lacks a dedicated endpoint to test device connections without saving. We need to add:

```java
// DeviceResource.java - New endpoint
@POST
@Path("/test")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Operation(summary = "Test device connection without saving")
public Uni<DeviceTestResponse> testConnection(DeviceRequest request) {
    // Create temporary connection, test, return result
}
```

**DeviceTestResponse DTO:**
```java
public record DeviceTestResponse(
    boolean success,
    String message,
    ConnectionStatus status,
    DeviceIdentificationDto identification, // if successful
    Instant testTime
) {}
```

### 7.2 Alternative: Client-Side Test

If backend changes are not desired initially, we can implement a workaround:
1. Create device with `enabled: false`
2. Call `/api/devices/{id}/info?refresh=true`
3. Delete device if user cancels
4. Or keep device if user confirms

This is less elegant but works without backend changes.

---

## 8. Docker Compose Configuration

```yaml
# docker-compose.yml (development)
version: '3.8'

services:
  frodo:
    build: .
    ports:
      - "8080:8080"
    environment:
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:firebirdsql://firebird:3050/frodo
      - FRODO_MODBUS_HOST=${MODBUS_HOST:-192.168.1.100}
      - FRODO_MODBUS_PORT=${MODBUS_PORT:-502}
    depends_on:
      - firebird
    networks:
      - frodo-network

  firebird:
    image: jacobalberty/firebird:latest
    environment:
      - ISC_PASSWORD=masterkey
      - FIREBIRD_DATABASE=frodo
    volumes:
      - firebird-data:/firebird
    networks:
      - frodo-network

  prometheus:
    image: prom/prometheus:v2.47.0
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'
    networks:
      - frodo-network

  grafana:
    image: grafana/grafana:10.2.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ALLOW_EMBEDDING=true
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana-data:/var/lib/grafana
      - ./config/grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - prometheus
    networks:
      - frodo-network

networks:
  frodo-network:
    driver: bridge

volumes:
  firebird-data:
  prometheus-data:
  grafana-data:
```

**Prometheus config (`config/prometheus.yml`):**
```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'frodo'
    static_configs:
      - targets: ['frodo:8080']
    metrics_path: '/q/metrics'
```

**Grafana datasource provisioning (`config/grafana/provisioning/datasources/prometheus.yml`):**
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

---

## 9. Implementation Checklist

### Phase 1: Foundation
- [ ] Install dependencies (MUI, Router, Zustand, React Query, Axios)
- [ ] Create MUI theme matching existing styles
- [ ] Set up React Router with routes
- [ ] Create MainLayout with responsive sidebar
- [ ] Create Sidebar component (drawer on mobile, persistent on desktop)
- [ ] Create TopBar component with hamburger menu
- [ ] Set up Axios client with base URL and interceptors
- [ ] Create notification store (Zustand)
- [ ] Create app store (Zustand) for sidebar state
- [ ] Create useResponsive hook

### Phase 2: Device Configuration
- [ ] Create DevicesView (responsive: table desktop, cards mobile)
- [ ] Create DeviceForm component (all fields, responsive layout)
- [ ] Create DeviceCreateView
- [ ] Create DeviceDetailView (tabs desktop, accordion mobile)
- [ ] Implement device detection dialog
- [ ] Implement connection test dialog
- [ ] Wire up deviceApi.js with React Query hooks
- [ ] Implement delete with confirmation dialog
- [ ] Add form validation (validators.js)

### Phase 3: Server-Side Metrics Collection
- [ ] Create database schema (FroMetricsConfig, FroMetricsParameter)
- [ ] Create Liquibase changelog for metrics tables
- [ ] Implement MetricsConfigEntity and MetricsParameterEntity
- [ ] Implement MetricsConfigRepository
- [ ] Implement MetricsScrapingService with scheduled scraping
- [ ] Register Prometheus gauges dynamically
- [ ] Implement MetricsConfigResource (REST API)
- [ ] Create MetricsConfigView (frontend)
- [ ] Create ParameterSelector component
- [ ] Create ScrapingIntervalInput component
- [ ] Create MetricsStatusCard component
- [ ] Wire up metricsApi.js with React Query hooks
- [ ] Extend config export/import to include metrics

### Phase 4: Dashboard
- [ ] Create DashboardView layout (responsive grid)
- [ ] Create DeviceStatusCard (connection status, last read)
- [ ] Create PowerMetricsCard (inverter data)
- [ ] Create BatteryStatusCard (storage data)
- [ ] Create GridStatusCard (grid import/export)
- [ ] Implement auto-refresh polling (5-10s)
- [ ] Add device selector dropdown
- [ ] Add manual refresh button
- [ ] Implement error handling for unreachable devices
- [ ] Add loading skeletons

### Phase 5: Grafana Integration
- [ ] Create docker-compose.yml with Prometheus + Grafana
- [ ] Create Prometheus config (prometheus.yml)
- [ ] Create Grafana provisioning configs
- [ ] Create GrafanaEmbed component (responsive iframe)
- [ ] Create GrafanaPanel wrapper component
- [ ] Implement grafanaService.js (URL builder)
- [ ] Add Grafana panels to DashboardView
- [ ] Create sample Grafana dashboards for PV metrics

### Phase 6: Settings & Config
- [ ] Create SettingsView (responsive sections)
- [ ] Display app info from /api/info
- [ ] Implement config export (download JSON with metrics)
- [ ] Implement config import (upload + restore with metrics)
- [ ] Display health check status
- [ ] Display connection pool stats

### Phase 7: Polish
- [ ] Create LoadingSpinner component
- [ ] Create ErrorAlert component
- [ ] Create StatusChip component
- [ ] Create ConfirmDialog component
- [ ] Create EmptyState component
- [ ] Implement toast notifications (Snackbar)
- [ ] Test responsive layouts on all breakpoints
- [ ] Test touch interactions on mobile
- [ ] Add ARIA labels for accessibility
- [ ] Test keyboard navigation
- [ ] Add loading skeletons for all cards

---

## 10. Open Questions & Decisions Needed

1. **Device Discovery Implementation**: Should we implement network scanning (e.g., ping sweep + Modbus connect attempts), or just validate single host/IP entries?

2. **Grafana Dashboard UIDs**: Do you want me to create sample Grafana dashboards as part of the implementation, or will you create them manually?

3. **Real-time Updates**: For dashboard metrics, should we use:
   - Simple polling (5-10s intervals) - **recommended for now**
   - WebSocket/SSE for push updates (requires backend changes)
   - Combination (polling with WebSocket fallback)

4. **Device Test Endpoint**: The backend doesn't have a dedicated `/api/devices/test` endpoint. Options:
   - Add backend endpoint for testing without saving (**recommended**)
   - Use POST with validation-only flag
   - Create temporary device, test, then delete

5. **Multi-device Dashboard**: Should the dashboard show:
   - Single selected device (dropdown selector) - **recommended for v1**
   - All devices in separate cards/sections
   - Aggregated metrics across all devices

6. **Grid Import/Export Metrics**: Which SunSpec model fields represent grid power? Need to confirm:
   - Model 122 (Status) has grid-related fields?
   - Or should we calculate from inverter AC power + battery charge/discharge?

7. **Metrics Scraping Defaults**: What should be the default parameters enabled for new devices?
   - All available parameters (comprehensive but verbose)
   - Common subset (W, WH, ChaState, etc.) - **recommended**
   - None (user must explicitly enable)

---

## 11. Estimated Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Foundation | 1-2 days | None |
| Phase 2: Device Config | 2-3 days | Phase 1 |
| Phase 3: Metrics Collection | 2-3 days | Phase 1, Phase 2 |
| Phase 4: Dashboard | 2-3 days | Phase 1, Phase 2 |
| Phase 5: Grafana | 2 days | Phase 3, Docker Compose |
| Phase 6: Settings | 1 day | Phase 2, Phase 3 |
| Phase 7: Polish | 1-2 days | All phases |
| **Total** | **11-16 days** | |

---

## 12. Summary

This plan provides a **complete, production-ready dashboard UI** for Frodo with:

- **Strict MVC separation** (models, views, services)
- **Modern React patterns** (hooks, functional components)
- **Material-UI** for professional, consistent styling
- **Mobile-first responsive design** with adaptive layouts
- **Full device CRUD** with detection and testing
- **Server-side metrics collection** with per-device configuration
- **Prometheus/Grafana integration** for time-series visualization
- **Real-time dashboard** with SunSpec metrics
- **Grafana panel embedding** for advanced visualization
- **Config import/export** for disaster recovery (including metrics config)
- **Robust error handling** and loading states
- **Accessibility compliance** (ARIA, keyboard navigation)

The architecture is **scalable and maintainable**, with clear separation of concerns and reusable components ready for future expansion (charts, alerts, multi-device aggregation).
