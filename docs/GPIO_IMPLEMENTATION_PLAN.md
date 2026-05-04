# GPIO-Based Grid Supply Strategy Implementation Plan

## Overview

Add a new `PRICE_CONTROLLED_GPIO` export block strategy that uses GPIO pins instead of Modbus
throttling when running on Raspberry Pi 5.

Key capabilities:
- **Multiple named GPIO pairs** (output + input pin) defined in `application.properties`
- **Per-device assignment** stored in the database — each device using `PRICE_CONTROLLED_GPIO`
  is linked to exactly one configured GPIO pair
- **Manual GUI output toggle** per pair for testing, directly driving the pin
- Output pin controls an external relay/switch; input pin monitors an external override switch
- Graceful fallback to Modbus throttling when GPIO is unavailable or no pair is assigned

**Technology:** JDK Foreign Function & Memory API + Linux GPIO character device ioctl
(zero external dependencies, follows `RPI5WireDevice.java` pattern)

---

## 1. Technology: Pure FFM + Linux GPIO Character Device API

### Approach

Direct `ioctl` calls to `/dev/gpiochipN` using GPIO v2 ABI (Linux kernel 5.10+).
No external library — only JDK 22+ and the Linux kernel GPIO subsystem.

### System Calls Needed
- `open()` — open `/dev/gpiochip0`
- `close()` — close file descriptors
- `ioctl()` — GPIO line request + set/get values

### GPIO v2 ioctl Commands
From `/usr/include/linux/gpio.h`:
```c
#define GPIO_V2_GET_LINE_IOCTL        _IOWR(0xB4, 0x07, struct gpio_v2_line_request)
#define GPIO_V2_LINE_GET_VALUES_IOCTL _IOWR(0xB4, 0x0E, struct gpio_v2_line_values)
#define GPIO_V2_LINE_SET_VALUES_IOCTL _IOWR(0xB4, 0x0F, struct gpio_v2_line_values)
```

Computed ioctl values (64-bit Linux/aarch64):
```java
private static final long GPIO_V2_GET_LINE_IOCTL        = 0xC250B407L;
private static final long GPIO_V2_LINE_GET_VALUES_IOCTL = 0xC010B40EL;
private static final long GPIO_V2_LINE_SET_VALUES_IOCTL = 0xC010B40FL;
```

### Key Structures

```c
struct gpio_v2_line_request {
    __u32 offsets[64];                          // GPIO line numbers (BCM pins)
    char  consumer[32];                          // Consumer label
    struct gpio_v2_line_config config;
    __u32 num_lines;
    __u32 event_buffer_size;
    __u32 padding[5];
    __s32 fd;                                    // Returned: line fd for get/set
};

struct gpio_v2_line_config {
    __aligned_u64 flags;                         // direction + bias flags
    __u32 num_attrs;
    __u32 padding[5];
    struct gpio_v2_line_config_attribute attrs[10];
};

struct gpio_v2_line_values {
    __aligned_u64 bits;                          // 1 = HIGH, 0 = LOW
    __aligned_u64 mask;                          // which lines to read/write
};
```

### Structure Layout (Memory Offsets)

**gpio_v2_line_request** (total: 592 bytes):

| Field              | Offset | Size |
|--------------------|--------|------|
| `offsets[64]`      | 0      | 256  |
| `consumer[32]`     | 256    | 32   |
| `config`           | 288    | 272  |
| `num_lines`        | 560    | 4    |
| `event_buffer_size`| 564    | 4    |
| `padding[5]`       | 568    | 20   |
| `fd`               | 588    | 4    |

**gpio_v2_line_config** (total: 272 bytes):

| Field        | Offset | Size |
|--------------|--------|------|
| `flags`      | 0      | 8    |
| `num_attrs`  | 8      | 4    |
| `padding[5]` | 12     | 20   |
| `attrs[10]`  | 32     | 240  |

**gpio_v2_line_values** (total: 16 bytes):

| Field  | Offset | Size |
|--------|--------|------|
| `bits` | 0      | 8    |
| `mask` | 8      | 8    |

### GPIO v2 Flags

```java
private static final long GPIO_V2_LINE_FLAG_INPUT        = 1L << 2;
private static final long GPIO_V2_LINE_FLAG_OUTPUT       = 1L << 3;
private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_UP   = 1L << 8;
private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_DOWN = 1L << 9;
private static final long GPIO_V2_LINE_FLAG_BIAS_DISABLED  = 1L << 10;
```

### Advantages
- ✅ **Zero dependencies** — no libgpiod, no Pi4J
- ✅ **Direct kernel interface** — maximum performance
- ✅ **Proven pattern** — follows `RPI5WireDevice.java` (I2C via FFM)
- ✅ **JDK-stable** — FFM is standard Java 22+

---

## 2. Configuration Properties

### Named GPIO Pair Structure

Pairs are named by an arbitrary key in `application.properties`:

```properties
# --- GPIO Export Control (RPi5 only) ---
frodo.gpio.enabled=false

# Shared settings
frodo.gpio.chip-device=/dev/gpiochip0
frodo.gpio.consumer-label=frodo-export-control

# Named pair "relay1"
frodo.gpio.pairs.relay1.output-pin=17
frodo.gpio.pairs.relay1.output-block-level=HIGH
frodo.gpio.pairs.relay1.input-pin=27
frodo.gpio.pairs.relay1.input-active-level=HIGH
frodo.gpio.pairs.relay1.input-bias=PULL_DOWN

# Named pair "relay2"
frodo.gpio.pairs.relay2.output-pin=22
frodo.gpio.pairs.relay2.output-block-level=HIGH
frodo.gpio.pairs.relay2.input-pin=23
frodo.gpio.pairs.relay2.input-active-level=HIGH
frodo.gpio.pairs.relay2.input-bias=PULL_DOWN
```

**Pair configuration fields:**

| Property              | Values                     | Default     | Description                             |
|-----------------------|----------------------------|-------------|-----------------------------------------|
| `output-pin`          | BCM pin number             | —           | Output pin controlling relay            |
| `output-block-level`  | `HIGH` / `LOW`             | `HIGH`      | Pin level when export is blocked        |
| `input-pin`           | BCM pin number             | —           | Input pin monitoring external switch    |
| `input-active-level`  | `HIGH` / `LOW`             | `HIGH`      | Pin level when external mode is active  |
| `input-bias`          | `PULL_UP` / `PULL_DOWN` / `DISABLE` / `AS_IS` | `PULL_DOWN` | Input pull resistor |

### Java `@ConfigMapping`

```java
@ConfigMapping(prefix = "frodo.gpio")
public interface GpioConfig {
  boolean enabled();

  @WithDefault("/dev/gpiochip0")
  String chipDevice();

  @WithDefault("frodo-export-control")
  String consumerLabel();

  Map<String, GpioPairConfig> pairs();

  interface GpioPairConfig {
    int outputPin();

    @WithDefault("HIGH")
    String outputBlockLevel();

    int inputPin();

    @WithDefault("HIGH")
    String inputActiveLevel();

    @WithDefault("PULL_DOWN")
    String inputBias();
  }
}
```

Pair names are arbitrary strings used as map keys. They must be valid MicroProfile Config
property keys (alphanumeric + hyphens recommended, no dots or spaces).

### Docker Compose / Kubernetes

SmallRye Config maps `frodo.gpio.pairs.relay1.output-pin` to the environment variable
`FRODO_GPIO_PAIRS_RELAY1_OUTPUT_PIN`:

```yaml
services:
  frodo:
    image: wolfgangreder/at.or.reder.frodo:latest
    devices:
      - /dev/gpiochip0:/dev/gpiochip0
    environment:
      FRODO_GPIO_ENABLED: "true"
      FRODO_GPIO_CHIP_DEVICE: "/dev/gpiochip0"
      FRODO_GPIO_PAIRS_RELAY1_OUTPUT_PIN: "17"
      FRODO_GPIO_PAIRS_RELAY1_OUTPUT_BLOCK_LEVEL: "HIGH"
      FRODO_GPIO_PAIRS_RELAY1_INPUT_PIN: "27"
      FRODO_GPIO_PAIRS_RELAY1_INPUT_ACTIVE_LEVEL: "HIGH"
      FRODO_GPIO_PAIRS_RELAY1_INPUT_BIAS: "PULL_DOWN"
      FRODO_GPIO_PAIRS_RELAY2_OUTPUT_PIN: "22"
      FRODO_GPIO_PAIRS_RELAY2_INPUT_PIN: "23"
```

**Note:** No `--privileged` flag needed — mapping character devices is sufficient.

---

## 3. New Enum Value

**File:** `src/main/java/at/or/reder/frodo/modbus/entity/ExportBlockStrategy.java`

```java
/**
 * GPIO-based price-controlled export (RPi5 only).
 *
 * <p>When the market price is negative, sets the GPIO output pin of the
 * configured pair to the block level, controlling an external relay/switch
 * instead of writing Modbus WMaxLim registers.  Monitors the paired GPIO
 * input pin to detect when an external override switch has taken control —
 * in that case, Modbus throttling is completely disabled (inverter runs at
 * 100%) and the system only reports state.</p>
 *
 * <p>Uses JDK Foreign Function & Memory API with Linux GPIO character
 * device ioctl for direct GPIO access via /dev/gpiochip* (zero external
 * dependencies).</p>
 *
 * <p>Requires {@code frodo.gpio.enabled=true}, a Raspberry Pi 5 with
 * Linux kernel 5.10+ (GPIO v2 ABI), and a GPIO pair assigned to the
 * device in the database.  Falls back to {@code PRICE_CONTROLLED}
 * (Modbus throttling) if GPIO is unavailable, initialisation fails, or
 * no pair is assigned to the device.</p>
 *
 * <p>Manual grid supply disable via REST API is always honoured regardless
 * of external switch state.</p>
 */
PRICE_CONTROLLED_GPIO
```

---

## 4. New Package: `at.or.reder.frodo.gpio`

### 4.1 `GpioConfig.java` (interface, `@ConfigMapping`)

See Section 2. Injected into `GpioService` as a single CDI bean.

### 4.2 `GpioPairState.java` (internal record, package-private)

Runtime state for one initialised pair:

```java
record GpioPairState(
  String name,
  int outputPin,
  String outputBlockLevel,
  int inputPin,
  String inputActiveLevel,
  int outputLineFd,
  int inputLineFd
) {}
```

### 4.3 `GpioPairStatus.java` (public record)

Status snapshot for one pair — used in REST responses and metrics:

```java
/**
 * Status snapshot for a single GPIO pair.
 *
 * @param name              Pair name as defined in application.properties
 * @param available         This pair is initialised and its lines are open
 * @param outputPin         BCM pin number of the output line
 * @param outputPinState    Current output pin level (null if unavailable)
 * @param outputManualOverride True when a manual test override is active
 * @param inputPin          BCM pin number of the input line
 * @param inputPinState     Current input pin level (null if unavailable)
 * @param externalModeActive Derived: input pin is at its active level
 * @param assignedDeviceId  Device this pair is currently assigned to (null = unassigned)
 * @param errorMessage      Non-null when available=false
 */
public record GpioPairStatus(
  String name,
  boolean available,
  int outputPin,
  Boolean outputPinState,
  boolean outputManualOverride,
  int inputPin,
  Boolean inputPinState,
  boolean externalModeActive,
  Long assignedDeviceId,
  String errorMessage
) {}
```

### 4.4 `GpioStatus.java` (public record)

System-level status, wrapping per-pair statuses:

```java
/**
 * GPIO system status snapshot.
 *
 * @param available      GPIO system initialised successfully (all pairs opened)
 * @param isRaspberryPi5 Platform detection result
 * @param platform       Platform description (e.g. "Raspberry Pi 5 Model B Rev 1.0")
 * @param errorMessage   System-level error message (null when available)
 * @param pairs          Status for every configured pair
 */
public record GpioStatus(
  boolean available,
  boolean isRaspberryPi5,
  String platform,
  String errorMessage,
  List<GpioPairStatus> pairs
) {}
```

### 4.5 `GpioService.java` (CDI `@ApplicationScoped`)

**Pattern:** Follow `RPI5WireDevice.java` exactly.

**Responsibilities:**
- Initialize FFM handles on construction (`open`, `close`, `ioctl`)
- Detect platform on startup (check `/proc/cpuinfo` for "Raspberry Pi 5")
- Open the GPIO chip device once; iterate `GpioConfig.pairs()` and open each pair's lines
- Track per-pair manual override flags for GUI testing
- Provide:
  - `boolean isAvailable()` — true if at least one pair is open
  - `boolean isPairAvailable(String pairName)`
  - `void setBlockState(String pairName, boolean blocked)` — write output pin
  - `void setManualOutput(String pairName, boolean high)` — GUI test override
  - `void clearManualOutput(String pairName)` — clear test override
  - `boolean isExternalModeActive(String pairName)` — read input pin
  - `GpioStatus getStatus()` — full system + per-pair snapshot
- Graceful degradation: a pair that fails to initialise is marked unavailable but
  does not block other pairs; the system continues with available pairs
- Shutdown hook: close all line fds, chip fd, arena

**Key Implementation Details:**

#### Field Declarations

```java
@ApplicationScoped
public class GpioService {

  private static final Logger LOG = Logger.getLogger(GpioService.class);

  // GPIO v2 ioctl command codes
  private static final long GPIO_V2_GET_LINE_IOCTL        = 0xC250B407L;
  private static final long GPIO_V2_LINE_GET_VALUES_IOCTL = 0xC010B40EL;
  private static final long GPIO_V2_LINE_SET_VALUES_IOCTL = 0xC010B40FL;

  // GPIO v2 line flags
  private static final long GPIO_V2_LINE_FLAG_INPUT        = 1L << 2;
  private static final long GPIO_V2_LINE_FLAG_OUTPUT       = 1L << 3;
  private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_UP   = 1L << 8;
  private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_DOWN = 1L << 9;
  private static final long GPIO_V2_LINE_FLAG_BIAS_DISABLED  = 1L << 10;

  // O_RDWR flag for open()
  private static final int O_RDWR = 2;

  // FFM handles (final, initialised in constructor, never null after successful init)
  private final MethodHandle open;
  private final MethodHandle close;
  private final MethodHandle ioctl;
  private final boolean ffmAvailable;

  // GPIO runtime state (written once during startup, read-only thereafter)
  private volatile boolean systemAvailable = false;
  private volatile boolean isRaspberryPi5 = false;
  private String platform = "Unknown";
  private String systemErrorMessage;

  // Chip fd and per-pair state (immutable after startup, concurrent reads safe)
  private int chipFd = -1;
  private Arena arena;
  private final Map<String, GpioPairState> pairStates = new ConcurrentHashMap<>();

  // Manual test override state per pair (GUI toggle)
  // null = no override, true = forced HIGH, false = forced LOW
  private final Map<String, Boolean> manualOverrides = new ConcurrentHashMap<>();

  @Inject
  GpioConfig gpioConfig;

  @Inject
  GpioDeviceAssignmentRepository assignmentRepository;
```

#### Constructor: Initialize FFM Handles

```java
  public GpioService() {
    boolean ok = false;
    MethodHandle openH = null, closeH = null, ioctlH = null;
    try {
      Linker linker = Linker.nativeLinker();
      SymbolLookup stdlib = linker.defaultLookup();

      // int open(const char *pathname, int flags)
      openH = linker.downcallHandle(
        stdlib.find("open").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
      );

      // int close(int fd)
      closeH = linker.downcallHandle(
        stdlib.find("close").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
      );

      // int ioctl(int fd, unsigned long request, void *argp)
      ioctlH = linker.downcallHandle(
        stdlib.find("ioctl").orElseThrow(),
        FunctionDescriptor.of(
          ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
          ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
      );
      ok = true;
    } catch (Throwable t) {
      LOG.errorf(t, "Failed to initialise GPIO FFM handles: %s", t.getMessage());
      this.systemErrorMessage = "FFM init failed: " + t.getMessage();
    }
    this.open = openH;
    this.close = closeH;
    this.ioctl = ioctlH;
    this.ffmAvailable = ok;
  }
```

#### Startup

```java
  void onStart(@Observes StartupEvent event) {
    if (!gpioConfig.enabled()) {
      LOG.info("GPIO export control disabled (frodo.gpio.enabled=false)");
      return;
    }
    if (!ffmAvailable) {
      LOG.error("GPIO enabled but FFM handles could not be initialised — GPIO unavailable");
      return;
    }

    detectPlatform();

    if (!isRaspberryPi5) {
      this.systemErrorMessage = "Not running on Raspberry Pi 5 (" + platform + ")";
      LOG.warnf("GPIO enabled but platform is not RPi5: %s — GPIO unavailable", platform);
      return;
    }

    if (gpioConfig.pairs().isEmpty()) {
      this.systemErrorMessage = "GPIO enabled but no pairs configured (frodo.gpio.pairs.*)";
      LOG.warn(systemErrorMessage);
      return;
    }

    try {
      this.arena = Arena.ofShared();
      openChip();
      initPairs();
      this.systemAvailable = true;
      LOG.infof("GPIO export control ready: %d pair(s) on %s",
        pairStates.size(), gpioConfig.chipDevice());
    } catch (Exception e) {
      LOG.errorf(e, "GPIO startup failed: %s", e.getMessage());
      this.systemErrorMessage = "Startup failed: " + e.getMessage();
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
    }
  }

  private void openChip() throws Exception {
    MemorySegment chipPath = arena.allocateFrom(gpioConfig.chipDevice());
    this.chipFd = (int) open.invoke(chipPath, O_RDWR);
    if (chipFd < 0) {
      throw new IOException("Failed to open GPIO chip: " + gpioConfig.chipDevice());
    }
    LOG.debugf("Opened GPIO chip fd=%d (%s)", chipFd, gpioConfig.chipDevice());
  }

  private void initPairs() {
    for (var entry : gpioConfig.pairs().entrySet()) {
      String name = entry.getKey();
      GpioConfig.GpioPairConfig cfg = entry.getValue();
      try {
        int outputFd = requestLine(
          cfg.outputPin(),
          GPIO_V2_LINE_FLAG_OUTPUT,
          "HIGH".equalsIgnoreCase(cfg.outputBlockLevel()) ? 0 : 1 // start unblocked
        );

        long inputFlags = GPIO_V2_LINE_FLAG_INPUT | biasFlag(cfg.inputBias());
        int inputFd = requestLine(cfg.inputPin(), inputFlags, 0);

        pairStates.put(name, new GpioPairState(
          name,
          cfg.outputPin(),
          cfg.outputBlockLevel(),
          cfg.inputPin(),
          cfg.inputActiveLevel(),
          outputFd,
          inputFd
        ));
        LOG.infof("GPIO pair '%s' ready: output=%d, input=%d", name, cfg.outputPin(), cfg.inputPin());
      } catch (Exception e) {
        LOG.errorf(e, "Failed to initialise GPIO pair '%s': %s — pair unavailable", name, e.getMessage());
        // Other pairs continue unaffected
      }
    }
  }

  private long biasFlag(String bias) {
    return switch (bias.toUpperCase()) {
      case "PULL_UP"   -> GPIO_V2_LINE_FLAG_BIAS_PULL_UP;
      case "PULL_DOWN" -> GPIO_V2_LINE_FLAG_BIAS_PULL_DOWN;
      case "DISABLE"   -> GPIO_V2_LINE_FLAG_BIAS_DISABLED;
      default          -> 0L; // AS_IS
    };
  }
```

#### Line Request and Value Read/Write

```java
  private int requestLine(int lineOffset, long flags, int defaultValue) throws Exception {
    // gpio_v2_line_request is 592 bytes (see struct layout above)
    MemorySegment req = arena.allocate(592);
    req.fill((byte) 0);

    req.set(ValueLayout.JAVA_INT, 0, lineOffset);                 // offsets[0]

    MemorySegment consumerSeg = req.asSlice(256, 32);             // consumer[32]
    byte[] consumerBytes = gpioConfig.consumerLabel()
      .getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < Math.min(consumerBytes.length, 31); i++) {
      consumerSeg.set(ValueLayout.JAVA_BYTE, i, consumerBytes[i]);
    }

    req.set(ValueLayout.JAVA_LONG, 288, flags);                   // config.flags
    req.set(ValueLayout.JAVA_INT,  296, 0);                       // config.num_attrs
    req.set(ValueLayout.JAVA_INT,  560, 1);                       // num_lines
    req.set(ValueLayout.JAVA_INT,  564, 0);                       // event_buffer_size

    int result = (int) ioctl.invoke(chipFd, GPIO_V2_GET_LINE_IOCTL, req);
    if (result < 0) {
      throw new IOException("ioctl GPIO_V2_GET_LINE_IOCTL failed for line " + lineOffset);
    }

    int lineFd = req.get(ValueLayout.JAVA_INT, 588);              // fd
    if (lineFd < 0) {
      throw new IOException("Kernel returned invalid line fd for line " + lineOffset);
    }

    if ((flags & GPIO_V2_LINE_FLAG_OUTPUT) != 0) {
      setLineValue(lineFd, defaultValue);
    }
    return lineFd;
  }

  private void setLineValue(int lineFd, int value) throws Exception {
    MemorySegment vals = arena.allocate(16);
    vals.set(ValueLayout.JAVA_LONG, 0, value != 0 ? 1L : 0L);    // bits
    vals.set(ValueLayout.JAVA_LONG, 8, 1L);                        // mask
    int result = (int) ioctl.invoke(lineFd, GPIO_V2_LINE_SET_VALUES_IOCTL, vals);
    if (result < 0) {
      throw new IOException("ioctl GPIO_V2_LINE_SET_VALUES_IOCTL failed on fd=" + lineFd);
    }
  }

  private int getLineValue(int lineFd) throws Exception {
    MemorySegment vals = arena.allocate(16);
    vals.set(ValueLayout.JAVA_LONG, 0, 0L);
    vals.set(ValueLayout.JAVA_LONG, 8, 1L);
    int result = (int) ioctl.invoke(lineFd, GPIO_V2_LINE_GET_VALUES_IOCTL, vals);
    if (result < 0) {
      throw new IOException("ioctl GPIO_V2_LINE_GET_VALUES_IOCTL failed on fd=" + lineFd);
    }
    return (vals.get(ValueLayout.JAVA_LONG, 0) & 1L) != 0 ? 1 : 0;
  }
```

#### Public API

```java
  /** True if GPIO system is available (RPi5, chip open, at least one pair ready). */
  public boolean isAvailable() {
    return systemAvailable;
  }

  /** True if the named pair is initialised and its lines are open. */
  public boolean isPairAvailable(String pairName) {
    return pairStates.containsKey(pairName);
  }

  /** Returns all configured pair names from application.properties. */
  public Set<String> getConfiguredPairNames() {
    return Collections.unmodifiableSet(gpioConfig.pairs().keySet());
  }

  /**
   * Sets the output pin of the named pair to the block or unblock level.
   * Called by the scheduler (automatic price-based control).
   *
   * <p>If a manual test override is active for this pair the call is ignored
   * and the manual level is preserved.</p>
   *
   * @throws IOException if the pair is unavailable or the ioctl call fails
   */
  public void setBlockState(String pairName, boolean blocked) throws IOException {
    if (manualOverrides.containsKey(pairName)) {
      LOG.debugf("GPIO pair '%s': setBlockState ignored — manual override is active", pairName);
      return;
    }
    writeOutputPin(pairName, blocked);
  }

  /**
   * GUI test override: directly drives the output pin HIGH or LOW.
   * Stores the override so the scheduler's {@code setBlockState()} calls are
   * suppressed until {@link #clearManualOutput} is called.
   *
   * @param pairName pair name
   * @param high     true = drive HIGH, false = drive LOW
   * @throws IOException if the pair is unavailable or the ioctl call fails
   */
  public void setManualOutput(String pairName, boolean high) throws IOException {
    GpioPairState state = requirePair(pairName);
    try {
      setLineValue(state.outputLineFd(), high ? 1 : 0);
      manualOverrides.put(pairName, high);
      LOG.infof("GPIO pair '%s': manual output set to %s", pairName, high ? "HIGH" : "LOW");
    } catch (Throwable t) {
      throw new IOException("Failed to set manual output for pair '" + pairName + "'", t);
    }
  }

  /**
   * Clears the manual test override for a pair.
   * The scheduler will resume automatic control on its next tick.
   */
  public void clearManualOutput(String pairName) {
    manualOverrides.remove(pairName);
    LOG.infof("GPIO pair '%s': manual override cleared", pairName);
  }

  /**
   * Reads the input pin of the named pair and returns whether the external
   * override switch is active.
   *
   * @throws IOException if the pair is unavailable or the ioctl call fails
   */
  public boolean isExternalModeActive(String pairName) throws IOException {
    GpioPairState state = requirePair(pairName);
    try {
      int value = getLineValue(state.inputLineFd());
      boolean active = "HIGH".equalsIgnoreCase(state.inputActiveLevel())
        ? (value == 1) : (value == 0);
      LOG.tracef("GPIO pair '%s' input: value=%d active=%b", pairName, value, active);
      return active;
    } catch (Throwable t) {
      throw new IOException("Failed to read input for pair '" + pairName + "'", t);
    }
  }

  /** Returns the full system + per-pair status snapshot. */
  public GpioStatus getStatus() {
    List<GpioPairStatus> pairStatusList = new ArrayList<>();

    for (var entry : gpioConfig.pairs().entrySet()) {
      String name = entry.getKey();
      GpioPairConfig cfg = entry.getValue();
      GpioPairState state = pairStates.get(name);

      if (state == null) {
        pairStatusList.add(new GpioPairStatus(
          name, false, cfg.outputPin(), null, false,
          cfg.inputPin(), null, false, null,
          "Pair initialisation failed"
        ));
        continue;
      }

      Boolean outState = null;
      Boolean inState = null;
      boolean extActive = false;
      try {
        outState = getLineValue(state.outputLineFd()) == 1;
      } catch (Throwable ignored) {}
      try {
        int inVal = getLineValue(state.inputLineFd());
        inState = (inVal == 1);
        extActive = "HIGH".equalsIgnoreCase(state.inputActiveLevel())
          ? (inVal == 1) : (inVal == 0);
      } catch (Throwable ignored) {}

      Long assignedDevice = assignmentRepository.findByPairName(name)
        .map(a -> a.deviceId)
        .orElse(null);

      pairStatusList.add(new GpioPairStatus(
        name, true,
        state.outputPin(), outState,
        manualOverrides.containsKey(name),
        state.inputPin(), inState, extActive,
        assignedDevice, null
      ));
    }

    return new GpioStatus(
      systemAvailable, isRaspberryPi5, platform, systemErrorMessage,
      Collections.unmodifiableList(pairStatusList)
    );
  }

  // ===== Private helpers =====

  private GpioPairState requirePair(String pairName) throws IOException {
    GpioPairState state = pairStates.get(pairName);
    if (state == null) {
      throw new IOException("GPIO pair '" + pairName + "' is not available");
    }
    return state;
  }

  private void writeOutputPin(String pairName, boolean blocked) throws IOException {
    GpioPairState state = requirePair(pairName);
    int level = blocked
      ? ("HIGH".equalsIgnoreCase(state.outputBlockLevel()) ? 1 : 0)
      : ("HIGH".equalsIgnoreCase(state.outputBlockLevel()) ? 0 : 1);
    try {
      setLineValue(state.outputLineFd(), level);
      LOG.debugf("GPIO pair '%s': output=%s (blocked=%b)", pairName,
        level == 1 ? "HIGH" : "LOW", blocked);
    } catch (Throwable t) {
      throw new IOException("Failed to write output for pair '" + pairName + "'", t);
    }
  }
```

#### Cleanup

```java
  void onStop(@Observes ShutdownEvent event) {
    cleanup();
  }

  private void cleanup() {
    for (GpioPairState state : pairStates.values()) {
      closeFd(state.outputLineFd(), "output line fd for pair '" + state.name() + "'");
      closeFd(state.inputLineFd(),  "input line fd for pair '"  + state.name() + "'");
    }
    pairStates.clear();

    closeFd(chipFd, "GPIO chip fd");
    chipFd = -1;

    if (arena != null) {
      try {
        arena.close();
      } catch (Exception e) {
        LOG.warnf("Failed to close arena: %s", e.getMessage());
      }
      arena = null;
    }

    systemAvailable = false;
  }

  private void closeFd(int fd, String label) {
    if (fd < 0) return;
    try {
      close.invoke(fd);
    } catch (Throwable t) {
      LOG.warnf("Failed to close %s (fd=%d): %s", label, fd, t.getMessage());
    }
  }
}
```

### 4.6 `GpioHealthCheck.java` (implements `HealthCheck`, `@Readiness`)

```java
@Readiness
@ApplicationScoped
public class GpioHealthCheck implements HealthCheck {

  @Inject GpioService gpioService;
  @Inject GpioConfig gpioConfig;

  @Override
  public HealthCheckResponse call() {
    HealthCheckResponseBuilder builder = HealthCheckResponse.named("GPIO Export Control");

    if (!gpioConfig.enabled()) {
      return builder.up().withData("enabled", false).build();
    }

    GpioStatus status = gpioService.getStatus();
    builder.withData("enabled", true)
           .withData("platform", status.platform())
           .withData("isRaspberryPi5", status.isRaspberryPi5())
           .withData("pairs", status.pairs().size());

    long unavailable = status.pairs().stream().filter(p -> !p.available()).count();
    if (!status.available() || unavailable > 0) {
      builder.down()
             .withData("unavailablePairs", unavailable)
             .withData("error", status.errorMessage() != null
               ? status.errorMessage() : unavailable + " pair(s) failed to initialise");
    } else {
      builder.up();
      status.pairs().forEach(p -> {
        builder.withData("pair." + p.name() + ".outputPin", p.outputPin());
        builder.withData("pair." + p.name() + ".inputPin",  p.inputPin());
        builder.withData("pair." + p.name() + ".externalModeActive", p.externalModeActive());
      });
    }

    return builder.build();
  }
}
```

### 4.7 `GpioMetrics.java`

Register one set of gauges per configured pair, using a `pair` tag:

```java
@ApplicationScoped
public class GpioMetrics {

  @Inject GpioService gpioService;
  @Inject GpioConfig gpioConfig;

  @Inject
  public void registerMetrics(MeterRegistry registry) {
    if (!gpioConfig.enabled()) return;

    // System-level gauge
    Gauge.builder("frodo_gpio_available", gpioService,
        s -> s.isAvailable() ? 1.0 : 0.0)
      .description("GPIO system availability")
      .register(registry);

    // Per-pair gauges
    for (String pairName : gpioConfig.pairs().keySet()) {
      String tag = pairName;

      Gauge.builder("frodo_gpio_pair_output_state", gpioService, s ->
          s.getStatus().pairs().stream()
           .filter(p -> p.name().equals(tag)).findFirst()
           .map(p -> p.outputPinState() == null ? -1.0 : (p.outputPinState() ? 1.0 : 0.0))
           .orElse(-1.0))
        .description("GPIO output pin state (1=HIGH, 0=LOW, -1=unavailable)")
        .tag("pair", pairName)
        .register(registry);

      Gauge.builder("frodo_gpio_pair_input_state", gpioService, s ->
          s.getStatus().pairs().stream()
           .filter(p -> p.name().equals(tag)).findFirst()
           .map(p -> p.inputPinState() == null ? -1.0 : (p.inputPinState() ? 1.0 : 0.0))
           .orElse(-1.0))
        .description("GPIO input pin state (1=HIGH, 0=LOW, -1=unavailable)")
        .tag("pair", pairName)
        .register(registry);

      Gauge.builder("frodo_gpio_pair_external_mode", gpioService, s ->
          s.getStatus().pairs().stream()
           .filter(p -> p.name().equals(tag)).findFirst()
           .map(p -> p.externalModeActive() ? 1.0 : 0.0)
           .orElse(0.0))
        .description("External override switch active")
        .tag("pair", pairName)
        .register(registry);

      Gauge.builder("frodo_gpio_pair_manual_override", gpioService, s ->
          s.getStatus().pairs().stream()
           .filter(p -> p.name().equals(tag)).findFirst()
           .map(p -> p.outputManualOverride() ? 1.0 : 0.0)
           .orElse(0.0))
        .description("Manual test override active")
        .tag("pair", pairName)
        .register(registry);
    }
  }
}
```

---

## 5. Database Schema Changes

### 5.1 New Entity: `GpioDeviceAssignmentEntity`

**File:** `src/main/java/at/or/reder/frodo/modbus/entity/GpioDeviceAssignmentEntity.java`

```java
/**
 * Assigns a GPIO pair (from application.properties) to a Modbus device.
 *
 * <p>When a device uses the {@code PRICE_CONTROLLED_GPIO} export strategy
 * the scheduler looks up this entity to determine which GPIO pair to use.
 * Both {@code deviceId} and {@code gpioPairName} are unique — one device
 * maps to exactly one pair, and each pair is used by at most one device.</p>
 *
 * <p>The {@code gpioPairName} must match a key in
 * {@code frodo.gpio.pairs.*}; there is intentionally no FK constraint to
 * configuration (configuration lives outside the DB).  If the named pair
 * is not configured at runtime the scheduler falls back to Modbus.</p>
 */
@Entity
@Table(
  name = "FroGpioAssignment",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_FroGpioAssignment_device", columnNames = "device_id"),
    @UniqueConstraint(name = "uk_FroGpioAssignment_pair",   columnNames = "gpio_pair_name")
  }
)
public class GpioDeviceAssignmentEntity extends PanacheEntity {

  /** FK to FroModbusDevice.id */
  @Column(name = "device_id", nullable = false)
  public Long deviceId;

  /**
   * Name of the GPIO pair as declared in {@code frodo.gpio.pairs.<name>.*}.
   * Max 64 characters to match practical config key lengths.
   */
  @Column(name = "gpio_pair_name", nullable = false, length = 64)
  public String gpioPairName;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @PrePersist
  @PreUpdate
  protected void onWrite() {
    updatedAt = Instant.now();
  }
}
```

### 5.2 New Repository: `GpioDeviceAssignmentRepository`

**File:** `src/main/java/at/or/reder/frodo/modbus/repository/GpioDeviceAssignmentRepository.java`

```java
@ApplicationScoped
public class GpioDeviceAssignmentRepository
    implements PanacheRepository<GpioDeviceAssignmentEntity> {

  public Optional<GpioDeviceAssignmentEntity> findByDeviceId(Long deviceId) {
    return find("deviceId", deviceId).firstResultOptional();
  }

  public Optional<GpioDeviceAssignmentEntity> findByPairName(String pairName) {
    return find("gpioPairName", pairName).firstResultOptional();
  }
}
```

### 5.3 Liquibase Changeset

**File:** `src/main/resources/db/changelog/` — new changeset file following existing naming convention:

```xml
<changeSet id="gpio-assignment-table" author="frodo">
  <createTable tableName="FroGpioAssignment">
    <column name="id"             type="BIGINT" autoIncrement="false">
      <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="device_id"      type="BIGINT">
      <constraints nullable="false"/>
    </column>
    <column name="gpio_pair_name" type="VARCHAR(64)">
      <constraints nullable="false"/>
    </column>
    <column name="updated_at"     type="TIMESTAMP">
      <constraints nullable="false"/>
    </column>
  </createTable>

  <createSequence sequenceName="FroGpioAssignment_SEQ" startValue="1" incrementBy="1"/>

  <addUniqueConstraint
    tableName="FroGpioAssignment"
    columnNames="device_id"
    constraintName="uk_FroGpioAssignment_device"/>

  <addUniqueConstraint
    tableName="FroGpioAssignment"
    columnNames="gpio_pair_name"
    constraintName="uk_FroGpioAssignment_pair"/>

  <addForeignKeyConstraint
    baseTableName="FroGpioAssignment"
    baseColumnNames="device_id"
    referencedTableName="FroModbusDevice"
    referencedColumnNames="id"
    constraintName="fk_FroGpioAssignment_device"
    onDelete="CASCADE"/>
</changeSet>
```

---

## 6. Modify `ExportSchedulerService.java`

### 6.1 Inject New Dependencies

```java
@Inject
GpioService gpioService;

@Inject
GpioDeviceAssignmentRepository gpioAssignmentRepository;
```

### 6.2 `applyPriceControlledGpioBlock(ExportScheduleEntity schedule)`

**Logic:**
1. Load the `GpioDeviceAssignmentEntity` for this device
   - If absent → fall back to Modbus (`applyPriceControlledBlock(schedule)`) + log warning
2. Check `gpioService.isPairAvailable(pairName)`
   - If unavailable → fall back to Modbus + log warning
3. Check `gpioService.isExternalModeActive(pairName)`
   - If YES → log "external override active, skipping control" and return (no Modbus, no GPIO)
4. Fetch current market price; determine `shouldBlock`
5. Call `gpioService.setBlockState(pairName, shouldBlock)` — this is a no-op if manual override is active
6. Update `lastApplied.put(deviceId, shouldBlock)`
7. Log result

```java
private void applyPriceControlledGpioBlock(ExportScheduleEntity schedule) {
  ModbusDeviceEntity device = loadDevice(schedule.deviceId);
  if (device == null || !device.enabled) {
    LOG.debugf("Skipping GPIO block for device %d: not found or disabled", schedule.deviceId);
    return;
  }

  // Resolve GPIO pair assignment
  Optional<GpioDeviceAssignmentEntity> assignmentOpt = loadGpioAssignment(schedule.deviceId);
  if (assignmentOpt.isEmpty()) {
    LOG.warnf(
      "Device %d (%s) uses PRICE_CONTROLLED_GPIO but has no GPIO pair assigned "
        + "— falling back to Modbus throttling",
      schedule.deviceId, device.name);
    applyPriceControlledBlock(schedule);
    return;
  }
  String pairName = assignmentOpt.get().gpioPairName;

  if (!gpioService.isPairAvailable(pairName)) {
    LOG.warnf(
      "GPIO pair '%s' for device %d (%s) is unavailable "
        + "— falling back to Modbus throttling",
      pairName, schedule.deviceId, device.name);
    applyPriceControlledBlock(schedule);
    return;
  }

  // Check external override switch
  try {
    if (gpioService.isExternalModeActive(pairName)) {
      LOG.infof(
        "External override active on pair '%s' for device %d (%s) "
          + "— skipping all control (GPIO + Modbus)",
        pairName, schedule.deviceId, device.name);
      lastApplied.put(schedule.deviceId, null);
      return;
    }
  } catch (IOException e) {
    LOG.errorf(e, "Failed to read GPIO input for pair '%s': %s", pairName, e.getMessage());
    // Continue — treat external mode as inactive and proceed
  }

  // Fetch price
  var priceOpt = marketPriceRepository.findCurrent();
  if (priceOpt.isEmpty()) {
    LOG.warnf("No market price available for device %d (%s) — skipping", schedule.deviceId, device.name);
    return;
  }

  double priceCt   = priceOpt.get().priceCt;
  boolean shouldBlock = shouldBlockForPrice(priceCt);

  try {
    gpioService.setBlockState(pairName, shouldBlock);
    lastApplied.put(schedule.deviceId, shouldBlock);
    LOG.infof(
      "GPIO applied: pair='%s' device=%d (%s) export=%s price=%.4f ct/kWh",
      pairName, schedule.deviceId, device.name,
      shouldBlock ? "BLOCKED" : "ENABLED", priceCt);
  } catch (IOException e) {
    LOG.errorf(e,
      "GPIO setBlockState failed for pair '%s' device %d (%s): %s — falling back to Modbus",
      pairName, schedule.deviceId, device.name, e.getMessage());
    applyPriceControlledBlock(schedule); // fallback
  }
}

@Transactional
Optional<GpioDeviceAssignmentEntity> loadGpioAssignment(Long deviceId) {
  return gpioAssignmentRepository.findByDeviceId(deviceId);
}
```

### 6.3 Modify `applyScheduleIfChanged()`

```java
if (schedule.strategy == ExportBlockStrategy.PRICE_CONTROLLED_GPIO) {
  if (manuallyEnabled.contains(schedule.deviceId)) {
    LOG.debugf("Skipping GPIO block for device %d: manual re-enable active", schedule.deviceId);
    return;
  }
  applyPriceControlledGpioBlock(schedule);
  return;
}
```

---

## 7. REST API

### 7.1 New Resource: `GpioResource.java`

**Path:** `/api/gpio`

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/gpio/status` | Full system + all pairs status |
| `GET`  | `/api/gpio/pairs` | List all configured pair names |
| `PUT`  | `/api/gpio/pairs/{name}/output` | Manual test output override |
| `DELETE` | `/api/gpio/pairs/{name}/output` | Clear manual test override |
| `GET`  | `/api/gpio/assignments` | List all device↔pair assignments |
| `GET`  | `/api/gpio/assignments/{deviceId}` | Get assignment for a device |
| `PUT`  | `/api/gpio/assignments/{deviceId}` | Create/update assignment |
| `DELETE` | `/api/gpio/assignments/{deviceId}` | Remove assignment |

**Manual output body:**
```json
{ "high": true }
```

**Assignment body:**
```json
{ "gpioPairName": "relay1" }
```

### 7.2 DTOs

**`GpioStatusDto.java`:**
```java
public record GpioStatusDto(
  boolean available,
  boolean isRaspberryPi5,
  String platform,
  String errorMessage,
  List<GpioPairStatusDto> pairs
) {}
```

**`GpioPairStatusDto.java`:**
```java
public record GpioPairStatusDto(
  String name,
  boolean available,
  int outputPin,
  Boolean outputPinState,
  boolean outputManualOverride,
  int inputPin,
  Boolean inputPinState,
  boolean externalModeActive,
  Long assignedDeviceId,
  String errorMessage
) {}
```

**`GpioManualOutputRequest.java`:**
```java
public record GpioManualOutputRequest(
  @NotNull boolean high
) {}
```

**`GpioAssignmentRequest.java`:**
```java
public record GpioAssignmentRequest(
  @NotNull @Size(min = 1, max = 64) String gpioPairName
) {}
```

**`GpioAssignmentDto.java`:**
```java
public record GpioAssignmentDto(
  Long deviceId,
  String gpioPairName,
  Instant updatedAt
) {}
```

### 7.3 Key Validations

- `PUT /api/gpio/pairs/{name}/output` — must return `404` if pair name not in configured pairs
- `PUT /api/gpio/assignments/{deviceId}` — must return `404` if device not found; `400` if pair name not in configured pairs
- `PUT /api/gpio/assignments/{deviceId}` — must return `409` if pair already assigned to another device

---

## 8. Frontend Changes (React)

### 8.1 API Client Methods

**File:** `src/main/webui/src/services/apiClient.js`

```javascript
export const gpioApi = {
  getStatus: ()               => apiClient.get('/api/gpio/status'),
  getPairs:  ()               => apiClient.get('/api/gpio/pairs'),

  setManualOutput: (name, high) =>
    apiClient.put(`/api/gpio/pairs/${name}/output`, { high }),
  clearManualOutput: (name)   =>
    apiClient.delete(`/api/gpio/pairs/${name}/output`),

  getAssignments:          ()           => apiClient.get('/api/gpio/assignments'),
  getAssignment:           (deviceId)   => apiClient.get(`/api/gpio/assignments/${deviceId}`),
  setAssignment:           (deviceId, gpioPairName) =>
    apiClient.put(`/api/gpio/assignments/${deviceId}`, { gpioPairName }),
  deleteAssignment:        (deviceId)   =>
    apiClient.delete(`/api/gpio/assignments/${deviceId}`),
};
```

### 8.2 Hooks

**`useGpioStatus.js`** — polls full status every 3 s:
```javascript
export function useGpioStatus() {
  return useQuery({
    queryKey: ['gpio', 'status'],
    queryFn: () => gpioApi.getStatus().then(r => r.data),
    refetchInterval: 3000,
  });
}
```

**`useGpioAssignments.js`** — device↔pair assignments:
```javascript
export function useGpioAssignments() {
  return useQuery({
    queryKey: ['gpio', 'assignments'],
    queryFn: () => gpioApi.getAssignments().then(r => r.data),
  });
}
```

**`useGpioManualOutput.js`** — mutations for manual toggle:
```javascript
export function useGpioManualOutput() {
  const queryClient = useQueryClient();
  return {
    set: useMutation({
      mutationFn: ({ name, high }) => gpioApi.setManualOutput(name, high),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: ['gpio', 'status'] }),
    }),
    clear: useMutation({
      mutationFn: ({ name }) => gpioApi.clearManualOutput(name),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: ['gpio', 'status'] }),
    }),
  };
}
```

### 8.3 New Page / Section: GPIO Management

**Location:** New `GpioPage.jsx` reachable from navigation (or a collapsible section in
`SettingsPage.jsx` — to be decided during implementation).

**Layout:**

```
┌─── GPIO Export Control ──────────────────────────────────────────┐
│ Platform: Raspberry Pi 5 Model B Rev 1.0   [Available: ✅]        │
│                                                                    │
│ ┌─ Pair: relay1 ────────────────────────────────────────────────┐ │
│ │  Output pin: 17   [■ HIGH]  [Manual: OFF]  [ Toggle HIGH/LOW ] │ │
│ │  Input pin:  27   [□ LOW ]  External mode: INACTIVE            │ │
│ │  Assigned device: Fronius Gen24 Plus (ID 1)  [ Reassign ]      │ │
│ └───────────────────────────────────────────────────────────────┘ │
│                                                                    │
│ ┌─ Pair: relay2 ────────────────────────────────────────────────┐ │
│ │  Output pin: 22   [□ LOW ]  [Manual: OFF]  [ Toggle HIGH/LOW ] │ │
│ │  Input pin:  23   [■ HIGH]  External mode: ⚠️ ACTIVE           │ │
│ │  Assigned device: (unassigned)             [ Assign ]          │ │
│ └───────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

**Components:**

- **`GpioSystemStatus`** — platform info + overall availability badge
- **`GpioPairCard`** — per-pair card showing:
  - Output pin number + current level (colour-coded: green=unblocked, red=blocked)
  - Manual override indicator + **Toggle button** (drives pin directly for testing)
  - "Clear override" button (shown only when manual override is active)
  - Input pin number + current level
  - External mode indicator (warning icon + banner when active)
  - Device assignment (device name if assigned, "unassigned" otherwise)
  - "Assign to device" / "Change assignment" / "Remove assignment" actions
- **`GpioAssignmentDialog`** — modal to select a device from the list of enabled devices

**Warnings / UX Rules:**
- When external mode is active: show amber warning banner "External override switch active — automatic control suspended"
- When manual override is active: show blue info banner "Manual test mode — scheduler writes are suppressed"
- `PRICE_CONTROLLED_GPIO` in strategy dropdown of `ExportScheduleEntity` editor: only shown when `gpioStatus.available === true`; tooltip explains RPi5-only requirement

### 8.4 `SettingsPage.jsx` / `DeviceScheduleEditor` Changes

When configuring an export schedule:
- If `gpioStatus.available === true` and `gpioStatus.pairs` is non-empty, add
  `PRICE_CONTROLLED_GPIO` to the strategy dropdown
- After selecting `PRICE_CONTROLLED_GPIO`, show a dropdown to pick which GPIO pair
  to assign to this device (calls `PUT /api/gpio/assignments/{deviceId}`)
- If the device already has a GPIO pair assigned, pre-select it

---

## 9. Testing Strategy

### 9.1 Unit Tests

**`GpioServiceTest.java`:**
- Test platform detection from mock `/proc/cpuinfo` content
- Test struct layout (verify field offsets match header values)
- Test `initPairs()` with two pairs — one succeeds, one fails: verify partial availability
- Test `setBlockState()` is suppressed when manual override is active
- Test `setManualOutput()` → manual override stored, correct level written
- Test `clearManualOutput()` → override removed
- Test `getStatus()` returns correct `assignedDeviceId` for each pair

**`ExportSchedulerServiceTest.java`:**
- Mock `GpioService` and `GpioDeviceAssignmentRepository`
- `applyPriceControlledGpioBlock()` scenarios:
  - No assignment → falls back to Modbus
  - Pair unavailable → falls back to Modbus
  - External mode active → no GPIO, no Modbus
  - Price negative + GPIO ok → GPIO set blocked
  - Price positive + GPIO ok → GPIO set unblocked
  - GPIO ioctl throws → falls back to Modbus
  - Manual override active → `setBlockState` no-op (scheduler still logs)

**`GpioResourceTest.java`** (`@QuarkusTest`):
- `GET /api/gpio/status` when disabled → `{ available: false }`
- `PUT /api/gpio/pairs/relay1/output` with unknown name → 404
- `PUT /api/gpio/assignments/1` with non-existent device → 404
- `PUT /api/gpio/assignments/1` with pair already assigned to device 2 → 409

### 9.2 Integration Tests (Manual, on RPi5)

1. Configure two pairs in `application.properties`
2. Start application; verify `GET /api/gpio/status` shows both pairs
3. Check `/sys/kernel/debug/gpio` — consumer labels visible
4. Use GUI toggle to drive output HIGH/LOW on each pair; measure with multimeter
5. Clear manual override; wait for next scheduler tick; verify automatic control resumes
6. Toggle external switch on each pair; verify GUI shows "External mode ACTIVE"
7. Assign each pair to a device; configure device with `PRICE_CONTROLLED_GPIO` strategy
8. Simulate negative price; verify GPIO output changes; verify no Modbus WMaxLim write

### 9.3 Docker Requirements

```yaml
services:
  frodo:
    image: wolfgangreder/at.or.reder.frodo:latest
    devices:
      - /dev/gpiochip0:/dev/gpiochip0
    environment:
      FRODO_GPIO_ENABLED: "true"
      FRODO_GPIO_PAIRS_RELAY1_OUTPUT_PIN: "17"
      FRODO_GPIO_PAIRS_RELAY1_INPUT_PIN:  "27"
      FRODO_GPIO_PAIRS_RELAY2_OUTPUT_PIN: "22"
      FRODO_GPIO_PAIRS_RELAY2_INPUT_PIN:  "23"
```

---

## 10. Implementation Order

### Phase 1: Core GPIO Service (2 days)
- [ ] Create `gpio/` package
- [ ] Implement `GpioConfig` (`@ConfigMapping`)
- [ ] Implement `GpioService` (FFM handles, platform detection, multi-pair init, API)
- [ ] Create `GpioPairState`, `GpioPairStatus`, `GpioStatus` records
- [ ] Add config properties to `application.properties`
- [ ] Implement `GpioHealthCheck`, `GpioMetrics`
- [ ] Test on RPi5: two pairs, measure outputs, read inputs

### Phase 2: Database (0.5 day)
- [ ] Add `GpioDeviceAssignmentEntity`
- [ ] Add `GpioDeviceAssignmentRepository`
- [ ] Write Liquibase changeset (`FroGpioAssignment` table + sequence)

### Phase 3: Scheduler Integration (1 day)
- [ ] Add `PRICE_CONTROLLED_GPIO` to `ExportBlockStrategy`
- [ ] Implement `applyPriceControlledGpioBlock()` in `ExportSchedulerService`
- [ ] Modify `applyScheduleIfChanged()`
- [ ] Unit tests (all scenarios)

### Phase 4: REST API (0.5 day)
- [ ] Implement `GpioResource` (status, pairs, manual output, assignments)
- [ ] Implement DTOs + validation
- [ ] `@QuarkusTest` endpoint tests

### Phase 5: Frontend (1.5 days)
- [ ] `gpioApi` in `apiClient.js`
- [ ] Hooks: `useGpioStatus`, `useGpioAssignments`, `useGpioManualOutput`
- [ ] `GpioPage.jsx` with `GpioSystemStatus`, `GpioPairCard`, `GpioAssignmentDialog`
- [ ] Navigation entry for GPIO page
- [ ] Strategy dropdown changes in schedule editor

### Phase 6: Documentation (0.5 day)
- [ ] Update `AGENTS.md` (GPIO section in Protocol & Domain Notes)
- [ ] Write `docs/GPIO_EXPORT_CONTROL.md` (hardware wiring, Docker, troubleshooting)

**Total Estimated Effort:** 6 days

---

## 11. Key Design Decisions

### 11.1 Multi-Pair Configuration: Map vs Indexed Array

**Decision:** `Map<String, GpioPairConfig>` with named keys

**Rationale:**
- Named keys are human-readable and survive re-ordering
- SmallRye Config `@ConfigMapping` handles `Map<String, Interface>` natively
- Environment variable mapping is straightforward (`FRODO_GPIO_PAIRS_RELAY1_OUTPUT_PIN`)
- Adding/removing a pair does not shift indices

### 11.2 Assignment Storage: DB vs Config File

**Decision:** DB table (`FroGpioAssignment`)

**Rationale:**
- Device–pair mapping changes at runtime without restarting
- Consistent with all other device configuration in frodo
- Allows the GUI to change assignments without touching config files
- Foreign key on `device_id` with `ON DELETE CASCADE` prevents orphaned rows

### 11.3 Manual Test Override: Transient vs Persistent

**Decision:** **Transient** (in-memory only, lost on restart)

**Rationale:**
- Testing purpose only — should not survive a restart
- No DB schema changes needed
- Scheduler resumes normal operation after restart without manual cleanup

### 11.4 Partial Failure: One Pair Fails Init

**Decision:** Log error for the failed pair, continue with remaining pairs

**Rationale:**
- A hardware problem on one pair should not break all other pairs
- `GpioStatus` clearly shows which pairs are unavailable
- Devices assigned to the failed pair fall back to Modbus throttling

### 11.5 Struct Layout: Manual Offsets

**Decision:** Manual byte offsets (as in `RPI5WireDevice.java`)

**Rationale:**
- Consistent with existing codebase
- Kernel ABI is stable
- `MemoryLayout` API adds significant verbosity with no runtime benefit here

---

## 12. Comparison: FFM vs Alternatives

| Aspect | Pure FFM + ioctl | libgpiod | Pi4J |
|--------|------------------|----------|------|
| **Dependencies** | **Zero** | libgpiod.so | 3 JARs |
| **Native library** | **None** | libgpiod.so.3 | libpigpio/libgpiod |
| **Performance** | **Native** | Native | Slight overhead |
| **Portability** | Linux kernel 5.10+ | libgpiod v2+ | RPi-specific |
| **Maintenance** | **JDK-stable** | libgpiod updates | Pi4J updates |
| **Pattern match** | **Exact** (RPI5WireDevice) | Different | Different |

---

## 13. RPi5 GPIO Specifics

### GPIO Chips
- `/dev/gpiochip0` — main GPIO bank (BCM 0-27, header pins 1-40)
- `/dev/gpiochip4` — additional GPIO bank

### Pin Numbering
BCM numbering (Broadcom chip-specific), not physical header numbering.
Example: BCM 17 = physical pin 11.

### Permissions
- Device group: `gpio` (user/container must have access)
- Docker: `--device /dev/gpiochip0:/dev/gpiochip0` is sufficient; no `--privileged`

### Debugging
```bash
ls -l /dev/gpiochip*          # list chips
cat /sys/kernel/debug/gpio    # shows consumer labels
uname -r                      # kernel version (need ≥ 5.10 for GPIO v2)
gpioinfo  gpiochip0           # requires gpiod tools (optional)
gpioget   gpiochip0 17
gpioset   gpiochip0 17=1
```

---

## 14. Safety Considerations

### Electrical Safety
- **Relay ratings:** Ensure relay handles inverter control voltage/current
- **Isolation:** Optocoupler or relay with galvanic isolation recommended
- **Fail-safe:** Design circuit so GPIO failure = export enabled (safe default)
- **Flyback diode:** Protect GPIO from relay coil back-EMF

### Software Safety
- **Startup state:** Output pin starts unblocked (export enabled)
- **Shutdown:** `cleanup()` closes fds; external relay falls back to its own fail-safe state
- **Manual override:** Always available via REST and GUI, regardless of automatic state

### Recommended Circuit (per pair)
```
RPi5 GPIO {output-pin} → 1kΩ → Optocoupler LED → GND
Optocoupler transistor → Relay coil → +5V
Flyback diode across relay coil (cathode to +5V)
Relay contacts → Inverter control input (floating = export enabled)
```

---

## 15. Future Enhancements (Post-MVP)

- Hardware debouncing (GPIO v2 `debounce_period_us` attribute)
- Edge detection / interrupt-driven input monitoring
- Multiple output-only pins (fan-out relays, no input)
- Refactor to `MemoryLayout` API for compile-time struct safety
- GPIO state change history in DB

---

## 16. References

- Linux GPIO v2 uAPI: https://www.kernel.org/doc/html/latest/userspace-api/gpio/gpio-v2-line-get-ioctl.html
- JDK FFM API (JEP 454): https://openjdk.org/jeps/454
- RPi5 pinout: https://pinout.xyz/
- `RPI5WireDevice.java` — I2C via FFM reference implementation
- `ExportSchedulerService.java` — existing price-controlled export logic
- `ExportBlockStrategy.java` — strategy enum

---

## 17. Glossary

| Term | Definition |
|------|------------|
| **FFM** | Foreign Function & Memory API (JDK 22+) — native interop without JNI |
| **ioctl** | Input/Output Control — system call for device-specific operations |
| **GPIO pair** | Named config entry with one output pin (relay control) + one input pin (override switch) |
| **BCM** | Broadcom chip-specific GPIO pin numbering |
| **Character device** | Linux device file for unbuffered I/O (`/dev/gpiochipN`) |
| **Arena** | FFM memory lifecycle manager |
| **Consumer label** | String identifying which process owns a GPIO line (visible in `/sys/kernel/debug/gpio`) |
| **Bias** | Internal pull-up/pull-down resistor configuration on an input line |
| **Manual override** | Transient GUI test mode that directly drives an output pin, suppressing scheduler writes |
| **External mode** | State where the input pin indicates an external switch has taken control |

---

## End of Implementation Plan

**Status:** Ready for implementation
**Branch:** `feature/gpio-export-control`
**Next step:** Start Phase 1 — Core GPIO Service
