# GPIO-Based Grid Supply Strategy Implementation Plan

## Overview
Add a new `PRICE_CONTROLLED_GPIO` export block strategy that uses GPIO pins instead of Modbus throttling when running on Raspberry Pi 5. The system will control an external relay/switch via GPIO output and monitor an external override switch via GPIO input.

**Technology:** JDK Foreign Function & Memory API with Linux GPIO character device ioctl (zero external dependencies)

---

## 1. Technology: Pure FFM + Linux GPIO Character Device API

### Approach
Direct `ioctl` calls to `/dev/gpiochipN` using GPIO v2 ABI (Linux kernel 5.10+)

### System Calls Needed
- `open()` — open `/dev/gpiochip0`
- `close()` — close file descriptor
- `ioctl()` — GPIO operations (get line, set/get values)

### GPIO v2 ioctl Commands
From `/usr/include/linux/gpio.h`:
```c
#define GPIO_V2_GET_LINE_IOCTL        _IOWR(0xB4, 0x07, struct gpio_v2_line_request)
#define GPIO_V2_LINE_GET_VALUES_IOCTL _IOWR(0xB4, 0x0E, struct gpio_v2_line_values)
#define GPIO_V2_LINE_SET_VALUES_IOCTL _IOWR(0xB4, 0x0F, struct gpio_v2_line_values)
```

Computed ioctl values (64-bit):
```java
private static final long GPIO_V2_GET_LINE_IOCTL = 0xC0D0B407L;
private static final long GPIO_V2_LINE_GET_VALUES_IOCTL = 0xC010B40EL;
private static final long GPIO_V2_LINE_SET_VALUES_IOCTL = 0xC010B40FL;
```

### Key Structures

```c
struct gpio_v2_line_request {
    __u32 offsets[64];           // GPIO line numbers (BCM pins)
    char consumer[32];           // Consumer label
    struct gpio_v2_line_config config;
    __u32 num_lines;             // Number of lines (1 for single pin)
    __u32 event_buffer_size;
    __u32 padding[5];
    __s32 fd;                    // Returned: line fd for get/set operations
};

struct gpio_v2_line_config {
    __aligned_u64 flags;         // GPIO_V2_LINE_FLAG_OUTPUT, GPIO_V2_LINE_FLAG_INPUT, etc.
    __u32 num_attrs;
    __u32 padding[5];
    struct gpio_v2_line_config_attribute attrs[10];
};

struct gpio_v2_line_values {
    __aligned_u64 bits;          // Bitmap: 1=HIGH, 0=LOW
    __aligned_u64 mask;          // Bitmap: which lines to read/write
};
```

### Structure Layout (Memory Offsets)

**gpio_v2_line_request** (total: 592 bytes):
- `offsets[64]`: 0-255 (64 * 4 bytes)
- `consumer[32]`: 256-287 (32 bytes)
- `config`: 288-559 (272 bytes)
- `num_lines`: 560-563 (4 bytes)
- `event_buffer_size`: 564-567 (4 bytes)
- `padding[5]`: 568-587 (20 bytes)
- `fd`: 588-591 (4 bytes)

**gpio_v2_line_config** (total: 272 bytes):
- `flags`: 0-7 (8 bytes, aligned)
- `num_attrs`: 8-11 (4 bytes)
- `padding[5]`: 12-31 (20 bytes)
- `attrs[10]`: 32-271 (10 * 24 bytes)

**gpio_v2_line_values** (total: 16 bytes):
- `bits`: 0-7 (8 bytes, aligned)
- `mask`: 8-15 (8 bytes, aligned)

### GPIO v2 Flags

```java
private static final long GPIO_V2_LINE_FLAG_INPUT = 1L << 2;
private static final long GPIO_V2_LINE_FLAG_OUTPUT = 1L << 3;
private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_UP = 1L << 8;
private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_DOWN = 1L << 9;
private static final long GPIO_V2_LINE_FLAG_BIAS_DISABLED = 1L << 10;
```

### Advantages
- ✅ **Zero dependencies** — no libgpiod, no Pi4J
- ✅ **Direct kernel interface** — maximum performance
- ✅ **Full control** — bias, debounce, edge detection all available
- ✅ **Proven pattern** — follows `RPI5WireDevice.java` (I2C via FFM)
- ✅ **JDK-stable** — FFM is standard Java 22+

---

## 2. Configuration Properties

Add to `src/main/resources/application.properties`:

```properties
# --- GPIO Export Control (RPi5 only) ---
# Enable GPIO-based export control (runtime check: only works on RPi5)
frodo.gpio.enabled=false

# GPIO chip device path (RPi5: /dev/gpiochip0 or /dev/gpiochip4)
frodo.gpio.chip-device=/dev/gpiochip0

# Output pin: controls external relay/switch (BCM/chip line number)
frodo.gpio.output-pin=17

# Logic level when export should be blocked (HIGH or LOW)
frodo.gpio.output-block-level=HIGH

# Input pin: monitors external override switch state (BCM/chip line number)
frodo.gpio.input-pin=27

# Logic level when external mode is active (HIGH or LOW)
frodo.gpio.input-active-level=HIGH

# Input pin bias (PULL_UP, PULL_DOWN, DISABLE, AS_IS)
frodo.gpio.input-bias=PULL_DOWN

# Consumer label (shown in /sys/kernel/debug/gpio)
frodo.gpio.consumer-label=frodo-export-control
```

### Docker Compose / Kubernetes

Override via environment variables:
```yaml
environment:
  FRODO_GPIO_ENABLED: "true"
  FRODO_GPIO_CHIP_DEVICE: "/dev/gpiochip0"
  FRODO_GPIO_OUTPUT_PIN: "17"
  FRODO_GPIO_OUTPUT_BLOCK_LEVEL: "HIGH"
  FRODO_GPIO_INPUT_PIN: "27"
  FRODO_GPIO_INPUT_ACTIVE_LEVEL: "HIGH"
  FRODO_GPIO_INPUT_BIAS: "PULL_DOWN"
devices:
  - /dev/gpiochip0:/dev/gpiochip0
  - /dev/gpiochip4:/dev/gpiochip4  # Optional, for other GPIO banks
```

**Note:** No `--privileged` flag needed — character device access is sufficient.

---

## 3. New Enum Value

**File:** `src/main/java/at/or/reder/frodo/modbus/entity/ExportBlockStrategy.java`

Add new enum constant:

```java
/**
 * GPIO-based price-controlled export (RPi5 only).
 *
 * <p>When the market price is negative, sets a GPIO output pin to control
 * an external relay/switch instead of writing Modbus WMaxLim registers.
 * Monitors a GPIO input pin to detect when an external override switch
 * has taken control — in that case, Modbus throttling is completely
 * disabled (inverter runs at 100%) and the system only reports state.</p>
 *
 * <p>Uses JDK Foreign Function & Memory API with Linux GPIO character
 * device ioctl for direct GPIO access via /dev/gpiochip* (zero external
 * dependencies).</p>
 *
 * <p>Requires {@code frodo.gpio.enabled=true} and Raspberry Pi 5 with
 * Linux kernel 5.10+ (GPIO v2 ABI). Falls back to {@code PRICE_CONTROLLED}
 * (Modbus throttling) if GPIO is unavailable or initialization fails.</p>
 *
 * <p>Manual grid supply disable via REST API is always honored regardless
 * of external switch state.</p>
 */
PRICE_CONTROLLED_GPIO
```

---

## 4. New Package: `at.or.reder.frodo.gpio`

### 4.1 `GpioService.java` (CDI `@ApplicationScoped`)

**Pattern:** Follow `RPI5WireDevice.java` structure exactly

**Responsibilities:**
- Initialize FFM handles on construction (`open`, `close`, `ioctl`)
- Detect platform on startup (check `/proc/cpuinfo` for "Raspberry Pi 5")
- Open GPIO chip device (`/dev/gpiochip0`)
- Request output line (digital output, initial state = unblocked)
- Request input line (digital input with bias)
- Provide methods:
  - `boolean isAvailable()` — true if GPIO initialized successfully on RPi5
  - `void setBlockState(boolean blocked)` — set output pin to block/unblock level
  - `boolean isExternalModeActive()` — read input pin state
  - `GpioStatus getStatus()` — return record with pin states, platform info, availability
- Graceful degradation: if init fails, log error and mark as unavailable
- Shutdown hook: close line fds, chip fd, arena

**Key Implementation Details:**

#### Constructor: Initialize FFM Handles
```java
public GpioService() {
  try {
    Linker linker = Linker.nativeLinker();
    SymbolLookup stdlib = linker.defaultLookup();
    
    // int open(const char *pathname, int flags)
    this.open = linker.downcallHandle(
      stdlib.find("open").orElseThrow(),
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
    );
    
    // int close(int fd)
    this.close = linker.downcallHandle(
      stdlib.find("close").orElseThrow(),
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
    );
    
    // int ioctl(int fd, unsigned long request, void *argp)
    this.ioctl = linker.downcallHandle(
      stdlib.find("ioctl").orElseThrow(),
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    );
  } catch (Throwable t) {
    LOG.errorf(t, "Failed to initialize GPIO FFM handles: %s", t.getMessage());
    this.errorMessage = "FFM initialization failed: " + t.getMessage();
  }
}
```

#### Startup: Platform Detection + GPIO Init
```java
void onStart(@Observes StartupEvent event) {
  if (!gpioEnabled) {
    LOG.info("GPIO export control disabled (frodo.gpio.enabled=false)");
    return;
  }
  
  detectPlatform();
  
  if (!isRaspberryPi5) {
    LOG.warnf("GPIO enabled but platform is not RPi5: %s — GPIO unavailable", platform);
    this.errorMessage = "Not running on Raspberry Pi 5";
    return;
  }
  
  if (outputPin.isEmpty() || inputPin.isEmpty()) {
    LOG.error("GPIO enabled but pins not configured");
    this.errorMessage = "GPIO pins not configured";
    return;
  }
  
  try {
    initializeGpio();
    this.available = true;
    LOG.infof("GPIO export control initialized: output=%d, input=%d, chip=%s",
      outputPin.get(), inputPin.get(), chipDevice);
  } catch (Exception e) {
    LOG.errorf(e, "Failed to initialize GPIO: %s", e.getMessage());
    this.errorMessage = "GPIO init failed: " + e.getMessage();
    cleanup();
  }
}

private void detectPlatform() {
  try {
    String cpuInfo = Files.readString(Path.of("/proc/cpuinfo"));
    this.isRaspberryPi5 = cpuInfo.contains("Raspberry Pi 5");
    this.platform = cpuInfo.lines()
      .filter(line -> line.startsWith("Model"))
      .findFirst()
      .map(line -> line.split(":", 2)[1].trim())
      .orElse("Unknown");
    LOG.infof("Platform detected: %s (RPi5=%b)", platform, isRaspberryPi5);
  } catch (Exception e) {
    LOG.warnf("Failed to detect platform: %s", e.getMessage());
    this.platform = "Unknown";
    this.isRaspberryPi5 = false;
  }
}
```

#### GPIO Initialization (ioctl-based)
```java
private void initializeGpio() throws Exception {
  this.arena = Arena.ofShared();
  
  // Open GPIO chip device
  MemorySegment chipPath = arena.allocateFrom(chipDevice);
  this.chipFd = (int) open.invoke(chipPath, 2); // O_RDWR = 2
  if (chipFd < 0) {
    throw new IOException("Failed to open GPIO chip: " + chipDevice);
  }
  LOG.debugf("Opened GPIO chip fd=%d", chipFd);
  
  // Request output line
  this.outputLineFd = requestLine(
    outputPin.get(),
    GPIO_V2_LINE_FLAG_OUTPUT,
    "HIGH".equalsIgnoreCase(outputBlockLevel) ? 0 : 1  // Start unblocked
  );
  LOG.debugf("Requested output line %d, fd=%d", outputPin.get(), outputLineFd);
  
  // Request input line
  long inputFlags = GPIO_V2_LINE_FLAG_INPUT;
  if ("PULL_UP".equalsIgnoreCase(inputBias)) {
    inputFlags |= GPIO_V2_LINE_FLAG_BIAS_PULL_UP;
  } else if ("PULL_DOWN".equalsIgnoreCase(inputBias)) {
    inputFlags |= GPIO_V2_LINE_FLAG_BIAS_PULL_DOWN;
  } else if ("DISABLE".equalsIgnoreCase(inputBias)) {
    inputFlags |= GPIO_V2_LINE_FLAG_BIAS_DISABLED;
  }
  
  this.inputLineFd = requestLine(inputPin.get(), inputFlags, 0);
  LOG.debugf("Requested input line %d, fd=%d, bias=%s", inputPin.get(), inputLineFd, inputBias);
}

private int requestLine(int lineOffset, long flags, int defaultValue) throws Exception {
  // Allocate gpio_v2_line_request structure (592 bytes)
  MemorySegment request = arena.allocate(592);
  
  // Zero-fill
  for (long i = 0; i < 592; i++) {
    request.set(ValueLayout.JAVA_BYTE, i, (byte) 0);
  }
  
  // Set offsets[0] = lineOffset (offset 0)
  request.set(ValueLayout.JAVA_INT, 0, lineOffset);
  
  // Set consumer (offset 256)
  MemorySegment consumerSegment = request.asSlice(256, 32);
  byte[] consumerBytes = consumerLabel.getBytes(StandardCharsets.UTF_8);
  for (int i = 0; i < Math.min(consumerBytes.length, 31); i++) {
    consumerSegment.set(ValueLayout.JAVA_BYTE, i, consumerBytes[i]);
  }
  
  // Set config.flags (offset 288)
  request.set(ValueLayout.JAVA_LONG, 288, flags);
  
  // Set config.num_attrs = 0 (offset 296)
  request.set(ValueLayout.JAVA_INT, 296, 0);
  
  // Set num_lines = 1 (offset 560)
  request.set(ValueLayout.JAVA_INT, 560, 1);
  
  // Set event_buffer_size = 0 (offset 564)
  request.set(ValueLayout.JAVA_INT, 564, 0);
  
  // Call ioctl GPIO_V2_GET_LINE_IOCTL
  int result = (int) ioctl.invoke(chipFd, GPIO_V2_GET_LINE_IOCTL, request);
  if (result < 0) {
    throw new IOException("Failed to request GPIO line " + lineOffset);
  }
  
  // Extract returned fd (offset 588)
  int lineFd = request.get(ValueLayout.JAVA_INT, 588);
  if (lineFd < 0) {
    throw new IOException("Invalid line fd returned: " + lineFd);
  }
  
  // If output line, set initial value
  if ((flags & GPIO_V2_LINE_FLAG_OUTPUT) != 0) {
    setLineValue(lineFd, defaultValue);
  }
  
  return lineFd;
}
```

#### Read/Write GPIO Values
```java
private void setLineValue(int lineFd, int value) throws Exception {
  // Allocate gpio_v2_line_values structure (16 bytes)
  MemorySegment values = arena.allocate(16);
  values.set(ValueLayout.JAVA_LONG, 0, value != 0 ? 1L : 0L);  // bits
  values.set(ValueLayout.JAVA_LONG, 8, 1L);                     // mask (bit 0 = line 0)
  
  int result = (int) ioctl.invoke(lineFd, GPIO_V2_LINE_SET_VALUES_IOCTL, values);
  if (result < 0) {
    throw new IOException("Failed to set GPIO line value");
  }
}

private int getLineValue(int lineFd) throws Exception {
  // Allocate gpio_v2_line_values structure (16 bytes)
  MemorySegment values = arena.allocate(16);
  values.set(ValueLayout.JAVA_LONG, 0, 0L);  // bits (output)
  values.set(ValueLayout.JAVA_LONG, 8, 1L);  // mask (bit 0 = line 0)
  
  int result = (int) ioctl.invoke(lineFd, GPIO_V2_LINE_GET_VALUES_IOCTL, values);
  if (result < 0) {
    throw new IOException("Failed to get GPIO line value");
  }
  
  long bits = values.get(ValueLayout.JAVA_LONG, 0);
  return (bits & 1L) != 0 ? 1 : 0;
}
```

#### Public API
```java
public boolean isAvailable() {
  return available;
}

public void setBlockState(boolean blocked) throws IOException {
  if (!available) {
    throw new IOException("GPIO not available");
  }
  
  int targetLevel = blocked
    ? ("HIGH".equalsIgnoreCase(outputBlockLevel) ? 1 : 0)
    : ("HIGH".equalsIgnoreCase(outputBlockLevel) ? 0 : 1);
  
  try {
    setLineValue(outputLineFd, targetLevel);
    LOG.debugf("GPIO output set: blocked=%b, level=%d", blocked, targetLevel);
  } catch (Throwable t) {
    throw new IOException("Failed to set GPIO output", t);
  }
}

public boolean isExternalModeActive() throws IOException {
  if (!available) {
    return false;
  }
  
  try {
    int value = getLineValue(inputLineFd);
    boolean active = "HIGH".equalsIgnoreCase(inputActiveLevel) ? (value == 1) : (value == 0);
    LOG.tracef("GPIO input read: value=%d, active=%b", value, active);
    return active;
  } catch (Throwable t) {
    throw new IOException("Failed to read GPIO input", t);
  }
}

public GpioStatus getStatus() {
  Boolean outputState = null;
  Boolean inputState = null;
  boolean externalActive = false;
  
  if (available) {
    try {
      int outVal = getLineValue(outputLineFd);
      outputState = (outVal == 1);
    } catch (Throwable t) {
      LOG.debugf("Failed to read output state: %s", t.getMessage());
    }
    
    try {
      int inVal = getLineValue(inputLineFd);
      inputState = (inVal == 1);
      externalActive = "HIGH".equalsIgnoreCase(inputActiveLevel) ? (inVal == 1) : (inVal == 0);
    } catch (Throwable t) {
      LOG.debugf("Failed to read input state: %s", t.getMessage());
    }
  }
  
  return new GpioStatus(
    available,
    isRaspberryPi5,
    platform,
    outputState,
    inputState,
    externalActive,
    errorMessage
  );
}
```

#### Cleanup
```java
void onStop(@Observes ShutdownEvent event) {
  cleanup();
}

private void cleanup() {
  if (outputLineFd >= 0) {
    try {
      close.invoke(outputLineFd);
    } catch (Throwable t) {
      LOG.warnf("Failed to close output line fd: %s", t.getMessage());
    }
    outputLineFd = -1;
  }
  
  if (inputLineFd >= 0) {
    try {
      close.invoke(inputLineFd);
    } catch (Throwable t) {
      LOG.warnf("Failed to close input line fd: %s", t.getMessage());
    }
    inputLineFd = -1;
  }
  
  if (chipFd >= 0) {
    try {
      close.invoke(chipFd);
    } catch (Throwable t) {
      LOG.warnf("Failed to close chip fd: %s", t.getMessage());
    }
    chipFd = -1;
  }
  
  if (arena != null) {
    try {
      arena.close();
    } catch (Exception e) {
      LOG.warnf("Failed to close arena: %s", e.getMessage());
    }
    arena = null;
  }
  
  this.available = false;
}
```

### 4.2 `GpioStatus.java` (record)

**File:** `src/main/java/at/or/reder/frodo/gpio/GpioStatus.java`

```java
package at.or.reder.frodo.gpio;

/**
 * GPIO system status snapshot.
 *
 * @param available GPIO system initialized successfully
 * @param isRaspberryPi5 Platform detection result
 * @param platform Platform description (e.g. "Raspberry Pi 5 Model B Rev 1.0")
 * @param outputPinState Current output pin state (null if unavailable)
 * @param inputPinState Current input pin state (null if unavailable)
 * @param externalModeActive Derived from inputPinState + config
 * @param errorMessage null if available, error description otherwise
 */
public record GpioStatus(
  boolean available,
  boolean isRaspberryPi5,
  String platform,
  Boolean outputPinState,
  Boolean inputPinState,
  boolean externalModeActive,
  String errorMessage
) {}
```

### 4.3 `GpioHealthCheck.java` (implements `HealthCheck`)

**File:** `src/main/java/at/or/reder/frodo/gpio/GpioHealthCheck.java`

```java
package at.or.reder.frodo.gpio;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Health check for GPIO export control system.
 *
 * <p>Reports UP when GPIO is disabled or successfully initialized.
 * Reports DOWN when GPIO is enabled but initialization failed.</p>
 */
@Readiness
@ApplicationScoped
public class GpioHealthCheck implements HealthCheck {

  @Inject
  GpioService gpioService;

  @ConfigProperty(name = "frodo.gpio.enabled", defaultValue = "false")
  boolean gpioEnabled;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named("GPIO Export Control");

    if (!gpioEnabled) {
      return builder.up()
        .withData("enabled", false)
        .build();
    }

    GpioStatus status = gpioService.getStatus();

    builder.withData("enabled", true)
      .withData("available", status.available())
      .withData("platform", status.platform())
      .withData("isRaspberryPi5", status.isRaspberryPi5());

    if (status.available()) {
      builder.up()
        .withData("outputPinState", status.outputPinState() != null ? status.outputPinState().toString() : "null")
        .withData("inputPinState", status.inputPinState() != null ? status.inputPinState().toString() : "null")
        .withData("externalModeActive", status.externalModeActive());
    } else {
      builder.down()
        .withData("error", status.errorMessage() != null ? status.errorMessage() : "Unknown error");
    }

    return builder.build();
  }
}
```

### 4.4 `GpioMetrics.java` (CDI `@ApplicationScoped`)

**File:** `src/main/java/at/or/reder/frodo/gpio/GpioMetrics.java`

```java
package at.or.reder.frodo.gpio;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Micrometer metrics for GPIO export control.
 *
 * <p>Registers gauges for GPIO availability and pin states.</p>
 */
@ApplicationScoped
public class GpioMetrics {

  @Inject
  GpioService gpioService;

  @ConfigProperty(name = "frodo.gpio.enabled", defaultValue = "false")
  boolean gpioEnabled;

  @Inject
  public void registerMetrics(MeterRegistry registry) {
    if (!gpioEnabled) {
      return;
    }

    Gauge.builder("frodo_gpio_available", gpioService, service -> service.isAvailable() ? 1.0 : 0.0)
      .description("GPIO system availability (1=available, 0=unavailable)")
      .register(registry);

    Gauge.builder("frodo_gpio_output_state", gpioService, service -> {
      GpioStatus status = service.getStatus();
      if (status.outputPinState() == null) return -1.0;
      return status.outputPinState() ? 1.0 : 0.0;
    })
      .description("GPIO output pin state (1=HIGH, 0=LOW, -1=unavailable)")
      .register(registry);

    Gauge.builder("frodo_gpio_input_state", gpioService, service -> {
      GpioStatus status = service.getStatus();
      if (status.inputPinState() == null) return -1.0;
      return status.inputPinState() ? 1.0 : 0.0;
    })
      .description("GPIO input pin state (1=HIGH, 0=LOW, -1=unavailable)")
      .register(registry);

    Gauge.builder("frodo_gpio_external_mode_active", gpioService, service -> {
      GpioStatus status = service.getStatus();
      return status.externalModeActive() ? 1.0 : 0.0;
    })
      .description("External override switch active (1=active, 0=inactive)")
      .register(registry);
  }
}
```

---

## 5. Modify `ExportSchedulerService.java`

### 5.1 Inject `GpioService`

```java
@Inject
GpioService gpioService;
```

### 5.2 New Method: `applyPriceControlledGpioBlock(ExportScheduleEntity schedule)`

**Logic:**
1. Check if external mode is active (`gpioService.isExternalModeActive()`)
   - If YES:
     - Log: "External override active for device X — skipping all control"
     - Update `lastApplied.put(deviceId, null)` (unknown state, externally controlled)
     - Return early (no Modbus writes, no GPIO writes)
2. Load device, check enabled
3. Fetch current market price
4. Determine if should block (`shouldBlockForPrice(priceCt)`)
5. If GPIO available (`gpioService.isAvailable()`):
   - Set GPIO output pin: `gpioService.setBlockState(shouldBlock)`
   - **Do NOT write Modbus** (GPIO controls external relay, inverter runs at 100%)
   - Update `lastApplied.put(deviceId, shouldBlock)`
   - Log: "GPIO strategy applied: device=X, export=BLOCKED/ENABLED (GPIO), price=Y ct/kWh"
6. If GPIO NOT available (fallback):
   - Log warning: "GPIO unavailable for device X — falling back to Modbus throttling"
   - Delegate to existing `applyPriceControlledBlock(schedule)` (Modbus-based)

**Implementation:**

```java
private void applyPriceControlledGpioBlock(ExportScheduleEntity schedule) {
  ModbusDeviceEntity device = loadDevice(schedule.deviceId);
  if (device == null || !device.enabled) {
    LOG.debugf("Skipping GPIO price-controlled block for device %d: not found or disabled",
      schedule.deviceId);
    return;
  }

  // Check if external override switch is active
  try {
    if (gpioService.isExternalModeActive()) {
      LOG.infof(
        "External override active for device %d (%s) — skipping all control (GPIO + Modbus)",
        schedule.deviceId, device.name);
      lastApplied.put(schedule.deviceId, null); // Unknown state, externally controlled
      return;
    }
  } catch (IOException e) {
    LOG.errorf(e, "Failed to read GPIO input for device %d (%s): %s",
      schedule.deviceId, device.name, e.getMessage());
    // Continue with fallback to Modbus
  }

  var priceOpt = marketPriceRepository.findCurrent();
  if (priceOpt.isEmpty()) {
    LOG.warnf(
      "No market price available for device %d (%s) — skipping GPIO price-controlled block",
      schedule.deviceId, device.name);
    return;
  }

  double priceCt = priceOpt.get().priceCt;
  boolean shouldBlock = shouldBlockForPrice(priceCt);

  // Try GPIO control first
  if (gpioService.isAvailable()) {
    try {
      gpioService.setBlockState(shouldBlock);
      lastApplied.put(schedule.deviceId, shouldBlock);
      LOG.infof(
        "GPIO strategy applied: device=%d (%s), export=%s (GPIO), price=%.4f ct/kWh",
        schedule.deviceId, device.name, shouldBlock ? "BLOCKED" : "ENABLED", priceCt);
      return; // Success — do NOT write Modbus
    } catch (IOException e) {
      LOG.errorf(e,
        "Failed to set GPIO output for device %d (%s): %s — falling back to Modbus throttling",
        schedule.deviceId, device.name, e.getMessage());
      // Fall through to Modbus fallback
    }
  } else {
    LOG.warnf(
      "GPIO unavailable for device %d (%s) — falling back to Modbus throttling",
      schedule.deviceId, device.name);
  }

  // Fallback: use Modbus throttling
  if (!shouldBlock) {
    // Price is zero or positive — clear manual override and re-enable on transition only
    manuallyEnabled.remove(schedule.deviceId);
    if (!Boolean.FALSE.equals(lastApplied.get(schedule.deviceId))) {
      applyReEnable(schedule.deviceId);
    }
  } else {
    // Price is negative — use dynamic load+battery demand limit
    int toleranceWatts = schedule.exportToleranceWatts != null ? schedule.exportToleranceWatts : 50;
    String strategyLabel = String.format(
      "PRICE_CONTROLLED_GPIO (fallback to Modbus), price=%.4f ct/kWh, tol=%d W",
      priceCt, Integer.valueOf(toleranceWatts));
    applyDynamicLimitWithTolerance(schedule.deviceId, device, toleranceWatts, strategyLabel);
  }
}
```

### 5.3 Modify `applyScheduleIfChanged()`

Add case for `PRICE_CONTROLLED_GPIO`:

```java
if (schedule.strategy == ExportBlockStrategy.PRICE_CONTROLLED_GPIO) {
  if (manuallyEnabled.contains(schedule.deviceId)) {
    LOG.debugf(
      "Skipping GPIO block for device %d: user has manually re-enabled export",
      schedule.deviceId);
    return;
  }
  applyPriceControlledGpioBlock(schedule);
  return;
}
```

### 5.4 Manual Override Handling

When user manually disables export via REST API (existing `notifyManualOverride()` method):
- If strategy is `PRICE_CONTROLLED_GPIO` and external mode is NOT active:
  - Set GPIO output to "unblock" level
  - Add to `manuallyEnabled` set
  - Do NOT write Modbus (inverter stays at 100%)
- If external mode IS active:
  - Log: "Cannot apply manual override — external switch is active"
  - Return error to user

**Note:** This logic should be added to the REST resource that calls `notifyManualOverride()`.

---

## 6. Database Schema Changes

**No schema changes required!** `ExportBlockStrategy` is stored as a string enum in `FroExportSchedule.strategy` column — new enum value `PRICE_CONTROLLED_GPIO` will be automatically supported.

---

## 7. REST API Changes

### 7.1 New Resource: `GpioResource.java`

**File:** `src/main/java/at/or/reder/frodo/api/GpioResource.java`

```java
package at.or.reder.frodo.api;

import at.or.reder.frodo.api.dto.GpioStatusDto;
import at.or.reder.frodo.gpio.GpioService;
import at.or.reder.frodo.gpio.GpioStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST endpoint for GPIO export control status.
 */
@Path("/api/gpio")
@Tag(name = "GPIO", description = "GPIO export control operations")
public class GpioResource {

  @Inject
  GpioService gpioService;

  @GET
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get GPIO status", description = "Returns current GPIO system status including pin states and platform info")
  public GpioStatusDto getStatus() {
    GpioStatus status = gpioService.getStatus();
    return new GpioStatusDto(
      status.available(),
      status.isRaspberryPi5(),
      status.platform(),
      status.outputPinState(),
      status.inputPinState(),
      status.externalModeActive(),
      status.errorMessage()
    );
  }
}
```

### 7.2 New DTO: `GpioStatusDto.java`

**File:** `src/main/java/at/or/reder/frodo/api/dto/GpioStatusDto.java`

```java
package at.or.reder.frodo.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * GPIO system status response.
 */
@Schema(description = "GPIO export control status")
public record GpioStatusDto(
  @Schema(description = "GPIO system initialized successfully")
  boolean available,
  
  @Schema(description = "Platform is Raspberry Pi 5")
  boolean isRaspberryPi5,
  
  @Schema(description = "Platform description", example = "Raspberry Pi 5 Model B Rev 1.0")
  String platform,
  
  @Schema(description = "Current output pin state (null if unavailable)")
  Boolean outputPinState,
  
  @Schema(description = "Current input pin state (null if unavailable)")
  Boolean inputPinState,
  
  @Schema(description = "External override switch is active")
  boolean externalModeActive,
  
  @Schema(description = "Error message if unavailable")
  String errorMessage
) {}
```

---

## 8. Frontend Changes (React)

### 8.1 New API Client Method

**File:** `src/main/webui/src/services/apiClient.js`

Add:
```javascript
export const gpioApi = {
  getStatus: () => apiClient.get('/api/gpio/status'),
};
```

### 8.2 New Hook: `useGpioStatus.js`

**File:** `src/main/webui/src/hooks/useGpioStatus.js`

```javascript
import { useQuery } from '@tanstack/react-query';
import { gpioApi } from '../services/apiClient';

export function useGpioStatus() {
  return useQuery({
    queryKey: ['gpio', 'status'],
    queryFn: () => gpioApi.getStatus(),
    refetchInterval: 5000, // Poll every 5s
  });
}
```

### 8.3 Modify `SettingsPage.jsx` (or wherever export schedules are configured)

- Fetch GPIO status via `useGpioStatus()`
- In strategy dropdown:
  - Show `PRICE_CONTROLLED_GPIO` option only if `gpioStatus.available === true`
  - Add tooltip: "GPIO-based control (RPi5 only) — uses external relay instead of Modbus throttling"
- Display GPIO status card:
  - Platform info
  - Output pin state (visual indicator: green = unblocked, red = blocked)
  - Input pin state (visual indicator: blue = external mode active)
  - Warning banner if external mode is active: "External override switch is active — automatic control disabled"

### 8.4 Modify `DashboardPage.jsx` (optional)

Add GPIO status widget showing:
- Current output pin state
- External mode indicator
- Last update timestamp

---

## 9. Testing Strategy

### 9.1 Unit Tests

**`GpioServiceTest.java`:**
- Mock `MethodHandle.invoke()` to simulate ioctl responses
- Test platform detection logic (`/proc/cpuinfo` parsing)
- Test struct layout (verify offsets match kernel ABI)
- Test pin state transitions (output set, input read)
- Test fallback when GPIO unavailable

**`ExportSchedulerServiceTest.java`:**
- Mock `GpioService`
- Test `applyPriceControlledGpioBlock()` with various scenarios:
  - GPIO available, price negative → GPIO set to block level
  - GPIO available, price positive → GPIO set to unblock level
  - GPIO unavailable → fallback to Modbus throttling
  - External mode active → no control applied
  - Manual override → GPIO set to unblock level

### 9.2 Integration Tests (Manual, on RPi5)

1. Deploy to RPi5 with `frodo.gpio.enabled=true`
2. Verify GPIO pins initialized correctly:
   ```bash
   # Check chip opened
   ls -l /dev/gpiochip0
   
   # Check lines requested (shows consumer label)
   cat /sys/kernel/debug/gpio
   ```
3. Configure device with `PRICE_CONTROLLED_GPIO` strategy
4. Simulate negative price → verify output pin goes HIGH/LOW (measure with multimeter)
5. Simulate positive price → verify output pin returns to opposite level
6. Toggle external switch → verify input pin detected, control disabled
7. Test manual override via REST API → verify GPIO responds
8. Test fallback: disable GPIO in config → verify Modbus throttling used instead

### 9.3 Docker Considerations

**GPIO access in Docker requires:**
```yaml
devices:
  - /dev/gpiochip0:/dev/gpiochip0
  - /dev/gpiochip4:/dev/gpiochip4  # Optional
```

**No `--privileged` flag needed** — character device access is sufficient.

**Document in README:**
```yaml
# docker-compose.yml (RPi5 only)
services:
  frodo:
    image: wolfgangreder/at.or.reder.frodo:latest
    devices:
      - /dev/gpiochip0:/dev/gpiochip0
      - /dev/gpiochip4:/dev/gpiochip4
    environment:
      FRODO_GPIO_ENABLED: "true"
      FRODO_GPIO_OUTPUT_PIN: "17"
      FRODO_GPIO_INPUT_PIN: "27"
```

---

## 10. Documentation Updates

### 10.1 `AGENTS.md`

Add section under "Protocol & Domain Notes":

```markdown
### GPIO Export Control (RPi5)
- Strategy `PRICE_CONTROLLED_GPIO` uses GPIO pins instead of Modbus throttling
- Output pin controls external relay (configurable active level)
- Input pin monitors external override switch (with pull resistor)
- Uses JDK Foreign Function & Memory API with Linux GPIO character device ioctl (zero dependencies)
- Requires `frodo.gpio.enabled=true` and Raspberry Pi 5 with Linux kernel 5.10+ (GPIO v2 ABI)
- Falls back to Modbus throttling if GPIO unavailable
- External mode disables all automatic control (manual override still works)
- Config: `frodo.gpio.*` properties (see application.properties)
- Reference: `docs/GPIO_IMPLEMENTATION_PLAN.md`, `docs/GPIO_EXPORT_CONTROL.md`
```

### 10.2 New File: `docs/GPIO_EXPORT_CONTROL.md`

Comprehensive user guide covering:
- Hardware wiring diagram (GPIO pinout, relay connection)
- Configuration examples (Docker, K8s, bare metal)
- Troubleshooting (permissions, platform detection, ioctl errors)
- Safety considerations (relay ratings, isolation, fail-safe design)

---

## 11. Implementation Order

### Phase 1: Core GPIO Service (2 days)
- [ ] Create `gpio/` package structure
- [ ] Implement `GpioService.java`:
  - [ ] FFM handles initialization (open, close, ioctl)
  - [ ] Platform detection (`/proc/cpuinfo` parsing)
  - [ ] GPIO chip open (`/dev/gpiochip0`)
  - [ ] Line request (`requestLine()` with struct layout)
  - [ ] Read/write values (`setLineValue()`, `getLineValue()`)
  - [ ] Public API (`isAvailable()`, `setBlockState()`, `isExternalModeActive()`, `getStatus()`)
  - [ ] Cleanup (close fds, arena)
- [ ] Create `GpioStatus.java` record
- [ ] Add config properties to `application.properties`
- [ ] Test on RPi5:
  - [ ] Verify chip opens
  - [ ] Verify lines requested (check `/sys/kernel/debug/gpio`)
  - [ ] Verify read/write works (measure with multimeter)

### Phase 2: Scheduler Integration (1 day)
- [ ] Add `PRICE_CONTROLLED_GPIO` to `ExportBlockStrategy.java`
- [ ] Implement `applyPriceControlledGpioBlock()` in `ExportSchedulerService.java`
- [ ] Modify `applyScheduleIfChanged()` to handle new strategy
- [ ] Add fallback logic (GPIO unavailable → Modbus throttling)
- [ ] Unit tests:
  - [ ] Mock `GpioService`
  - [ ] Test all scenarios (GPIO available, unavailable, external mode, manual override)

### Phase 3: REST API + Metrics (0.5 day)
- [ ] Create `GpioResource.java` with `GET /api/gpio/status`
- [ ] Create `GpioStatusDto.java`
- [ ] Create `GpioHealthCheck.java`
- [ ] Create `GpioMetrics.java`
- [ ] Test endpoint on RPi5

### Phase 4: Frontend (1 day)
- [ ] Add `gpioApi` to `src/main/webui/src/services/apiClient.js`
- [ ] Create `src/main/webui/src/hooks/useGpioStatus.js`
- [ ] Update settings page:
  - [ ] Add GPIO status card
  - [ ] Show `PRICE_CONTROLLED_GPIO` in strategy dropdown (only if available)
  - [ ] Add external mode warning banner
- [ ] Optional: Add GPIO status widget to dashboard

### Phase 5: Documentation (0.5 day)
- [ ] Update `AGENTS.md` with GPIO section
- [ ] Write `docs/GPIO_EXPORT_CONTROL.md` (user guide)
- [ ] Update Docker Compose examples
- [ ] Add troubleshooting section

**Total Estimated Effort:** 5 days

---

## 12. Key Design Decisions

### 12.1 Struct Layout: Manual Offsets vs MemoryLayout API

**Decision:** Use **manual offsets** (proven in `RPI5WireDevice.java`)

**Rationale:**
- Faster development (no need to define complex `MemoryLayout` trees)
- Proven pattern (your I2C code already uses this approach)
- Kernel ABI is stable (offsets won't change)
- Can refactor to `MemoryLayout` later if needed

**Alternative:** Use `MemoryLayout` + `VarHandle` for type-safe field access (more verbose, compile-time checked)

### 12.2 ioctl Command Values: Hardcoded vs Runtime Computed

**Decision:** **Hardcode** computed values (0xC0D0B407L, etc.)

**Rationale:**
- Kernel ABI is stable (ioctl numbers won't change)
- Simpler code (no macro expansion logic)
- Can add runtime verification check if paranoid

**Alternative:** Compute at runtime from struct size using `_IOWR` macro formula

### 12.3 Debouncing: Hardware vs Software

**Decision:** **Software debouncing** (3 reads with 10ms delay, majority vote)

**Rationale:**
- Simpler FFM code (no need for GPIO v2 line attributes)
- Sufficient for switch debouncing (typical bounce time: 5-20ms)
- Can upgrade to hardware debouncing later if needed

**Alternative:** Use GPIO v2 `debounce_period_us` attribute (requires more complex struct layout)

### 12.4 Error Recovery: Retry vs Fallback

**Decision:** **Retry 3x with exponential backoff, then fall back to Modbus**

**Rationale:**
- Transient ioctl errors (e.g., EINTR) should be retried
- Persistent errors (e.g., device removed) should trigger fallback
- Exponential backoff prevents tight retry loops

**Implementation:**
```java
private void setBlockStateWithRetry(boolean blocked) throws IOException {
  int attempts = 0;
  long delayMs = 100;
  
  while (attempts < 3) {
    try {
      setBlockState(blocked);
      return; // Success
    } catch (IOException e) {
      attempts++;
      if (attempts >= 3) {
        throw e; // Give up, trigger fallback
      }
      LOG.warnf("GPIO write failed (attempt %d/3): %s — retrying in %dms",
        attempts, e.getMessage(), delayMs);
      Thread.sleep(delayMs);
      delayMs *= 2; // Exponential backoff
    }
  }
}
```

---

## 13. Comparison: FFM vs Alternatives

| Aspect | Pure FFM + ioctl | libgpiod | Pi4J |
|--------|------------------|----------|------|
| **Dependencies** | **Zero** | libgpiod.so | 3 JARs |
| **Native library** | **None** (kernel only) | libgpiod.so.3 | libpigpio/libgpiod |
| **Complexity** | Medium (struct layout) | Low (C API) | Low (Java API) |
| **Performance** | **Native** | Native | Slight overhead |
| **Portability** | Linux kernel 5.10+ | libgpiod v2+ | RPi-specific |
| **Maintenance** | **JDK-stable** | libgpiod updates | Pi4J updates |
| **Pattern match** | **Exact** (RPI5WireDevice) | Different | Different |
| **Learning curve** | Medium (FFM API) | Low (C docs) | Low (Java docs) |
| **Debugging** | Medium (ioctl errors) | Easy (C errno) | Easy (Java exceptions) |

**Recommendation:** Pure FFM + ioctl (matches your existing codebase, zero dependencies)

---

## 14. RPi5 GPIO Specifics

### GPIO Chips
- `/dev/gpiochip0` — Main GPIO bank (BCM 0-27, physical pins 1-40)
- `/dev/gpiochip4` — Additional GPIO bank (BCM 28+)

### Pin Numbering
- **BCM numbering** (Broadcom chip-specific) — used by kernel, libgpiod, this implementation
- **Physical numbering** (1-40 on header) — used by some tutorials, NOT used here

**Example:** BCM 17 = Physical pin 11

### Permissions
- GPIO character devices require read/write access: `/dev/gpiochip*`
- Default group: `gpio` (user must be in this group)
- Docker: map device with `--device /dev/gpiochip0:/dev/gpiochip0`

### Debugging Commands
```bash
# List GPIO chips
ls -l /dev/gpiochip*

# Show all GPIO lines and their consumers
cat /sys/kernel/debug/gpio

# Check kernel version (GPIO v2 requires 5.10+)
uname -r

# Test GPIO access (requires gpiod tools)
gpioinfo gpiochip0
gpioget gpiochip0 17
gpioset gpiochip0 17=1
```

---

## 15. Safety Considerations

### Electrical Safety
- **Relay ratings:** Ensure relay can handle inverter control voltage/current
- **Isolation:** Use optocoupler or relay with galvanic isolation
- **Fail-safe:** Design circuit so GPIO failure = export enabled (safe default)
- **Flyback diode:** Add diode across relay coil to protect GPIO from back-EMF

### Software Safety
- **Startup state:** Output pin starts in "unblocked" state (export enabled)
- **Shutdown:** Cleanup sets output to "unblocked" before closing
- **Watchdog:** Consider external watchdog that re-enables export if no GPIO activity for N seconds
- **Manual override:** Always allow manual control via REST API, regardless of GPIO state

### Recommended Circuit
```
RPi5 GPIO 17 (output) → 1kΩ resistor → Optocoupler LED → GND
Optocoupler transistor → Relay coil → +5V
Relay contacts → Inverter control input
Flyback diode across relay coil (cathode to +5V)
```

---

## 16. Future Enhancements

### Phase 2 (Post-MVP)
- [ ] Hardware debouncing (GPIO v2 line attributes)
- [ ] Edge detection (interrupt-driven input monitoring)
- [ ] Multiple output pins (control multiple relays)
- [ ] PWM output (analog control via pulse-width modulation)
- [ ] Refactor to `MemoryLayout` API (type-safe struct access)

### Phase 3 (Advanced)
- [ ] GPIO event logging to database (state change history)
- [ ] Configurable retry/fallback strategy
- [ ] GPIO pin auto-detection (scan available pins)
- [ ] Support for other platforms (Orange Pi, Banana Pi, etc.)

---

## 17. References

### Linux Kernel Documentation
- GPIO character device ABI: https://www.kernel.org/doc/html/latest/driver-api/gpio/using-gpio.html
- GPIO v2 uAPI: https://www.kernel.org/doc/html/latest/userspace-api/gpio/gpio-v2-line-get-ioctl.html
- ioctl reference: `man 2 ioctl`

### JDK FFM API
- Foreign Function & Memory API: https://openjdk.org/jeps/454
- `Arena` lifecycle: https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/Arena.html
- `MemoryLayout` API: https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/MemoryLayout.html

### Raspberry Pi 5
- GPIO pinout: https://pinout.xyz/
- RPi5 documentation: https://www.raspberrypi.com/documentation/computers/raspberry-pi-5.html
- GPIO character device migration: https://www.raspberrypi.com/documentation/computers/os.html#gpio-and-the-40-pin-header

### Existing Codebase
- `RPI5WireDevice.java` — I2C via FFM (reference implementation)
- `ExportSchedulerService.java` — Price-controlled export logic
- `ExportBlockStrategy.java` — Strategy enum

---

## 18. Glossary

| Term | Definition |
|------|------------|
| **FFM** | Foreign Function & Memory API (JDK 22+) — native interop without JNI |
| **ioctl** | Input/Output Control — system call for device-specific operations |
| **GPIO** | General Purpose Input/Output — programmable digital pins |
| **BCM** | Broadcom chip-specific pin numbering (used by kernel) |
| **Character device** | Linux device file that provides unbuffered I/O (`/dev/gpiochipN`) |
| **Arena** | FFM memory lifecycle manager (replaces manual malloc/free) |
| **Line** | GPIO pin (kernel terminology) |
| **Consumer** | Label identifying which process owns a GPIO line |
| **Bias** | Internal pull-up/pull-down resistor configuration |
| **Debounce** | Filter to remove electrical noise from switch contacts |

---

## End of Implementation Plan

**Status:** Ready for implementation  
**Next Steps:** Create feature branch, implement Phase 1 (Core GPIO Service)  
**Questions:** Contact maintainer before starting implementation
