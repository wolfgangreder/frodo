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

package at.or.reder.frodo.gpio;

import at.or.reder.frodo.modbus.repository.GpioDeviceAssignmentRepository;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GPIO service for controlling external relays via Linux GPIO character device API.
 *
 * <p>Uses JDK Foreign Function & Memory API to issue {@code ioctl} calls directly
 * to {@code /dev/gpiochipN} using the GPIO v2 ABI (Linux kernel 5.10+).
 * Zero external dependencies — follows the {@code RPI5WireDevice.java} pattern.</p>
 *
 * <p>Supports multiple named GPIO pairs (output + input), each independently
 * initialised. A pair that fails to initialise is marked unavailable but does
 * not block other pairs.</p>
 *
 * <p>Thread-safety: pair states are written once during startup and read-only
 * thereafter. Manual override flags use a {@link ConcurrentHashMap}. The
 * {@code ioctl} calls themselves are serialised per file descriptor by the
 * kernel.</p>
 */
@ApplicationScoped
public class GpioService {

  private static final Logger LOG = Logger.getLogger(GpioService.class);

  // GPIO v2 ioctl command codes (64-bit Linux/aarch64)
  private static final long GPIO_V2_GET_LINE_IOCTL        = 0xC250B407L;
  private static final long GPIO_V2_LINE_GET_VALUES_IOCTL = 0xC010B40EL;
  private static final long GPIO_V2_LINE_SET_VALUES_IOCTL = 0xC010B40FL;

  // GPIO v2 line flags
  private static final long GPIO_V2_LINE_FLAG_INPUT          = 1L << 2;
  private static final long GPIO_V2_LINE_FLAG_OUTPUT         = 1L << 3;
  private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_UP   = 1L << 8;
  private static final long GPIO_V2_LINE_FLAG_BIAS_PULL_DOWN = 1L << 9;
  private static final long GPIO_V2_LINE_FLAG_BIAS_DISABLED  = 1L << 10;

  // O_RDWR flag for open()
  private static final int O_RDWR = 2;

  // FFM handles (final, initialised in constructor, never null after successful init)
  private final MethodHandle openHandle;
  private final MethodHandle closeHandle;
  private final MethodHandle ioctlHandle;
  private final boolean ffmAvailable;

  // GPIO runtime state (written once during startup, read-only thereafter)
  private volatile boolean systemAvailable = false;
  private volatile boolean isRaspberryPi = false;
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

  /**
   * Constructor: initialise FFM method handles for {@code open}, {@code close},
   * and {@code ioctl} system calls. If FFM is unavailable (e.g. unsupported
   * platform), the service degrades gracefully.
   */
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
    }
    this.openHandle = openH;
    this.closeHandle = closeH;
    this.ioctlHandle = ioctlH;
    this.ffmAvailable = ok;

    if (!ok) {
      this.systemErrorMessage = "FFM init failed";
    }
  }

  // ========== Lifecycle ==========

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

    if (!isRaspberryPi) {
      this.systemErrorMessage = "Not running on Raspberry Pi (" + platform + ")";
      LOG.warnf("GPIO enabled but platform is not a Raspberry Pi: %s — GPIO unavailable", platform);
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
      this.systemAvailable = !pairStates.isEmpty();
      if (systemAvailable) {
        LOG.infof("GPIO export control ready: %d pair(s) on %s",
          pairStates.size(), gpioConfig.chipDevice());
      } else {
        this.systemErrorMessage = "All GPIO pairs failed to initialise";
        LOG.error(systemErrorMessage);
      }
    } catch (Exception e) {
      LOG.errorf(e, "GPIO startup failed: %s", e.getMessage());
      this.systemErrorMessage = "Startup failed: " + e.getMessage();
      cleanup();
    }
  }

  void onStop(@Observes ShutdownEvent event) {
    cleanup();
  }

  // ========== Public API ==========

  /** True if GPIO system is available (RPi5, chip open, at least one pair ready). */
  public boolean isAvailable() {
    return systemAvailable;
  }

  /** True if named pair is initialised and its lines are open. */
  public boolean isPairAvailable(String pairName) {
    return pairStates.containsKey(pairName);
  }

  /** Returns all configured pair names from application.properties. */
  public Set<String> getConfiguredPairNames() {
    return Collections.unmodifiableSet(gpioConfig.pairs().keySet());
  }

  /**
   * Sets output pin of named pair to block or unblock level.
   * Called by scheduler (automatic price-based control).
   *
   * <p>If manual test override is active for this pair, call is ignored
   * and manual level is preserved.</p>
   *
   * @throws IOException if pair is unavailable or ioctl call fails
   */
  public void setBlockState(String pairName, boolean blocked) throws IOException {
    if (manualOverrides.containsKey(pairName)) {
      LOG.debugf("GPIO pair '%s': setBlockState ignored — manual override is active", pairName);
      return;
    }
    writeOutputPin(pairName, blocked);
  }

  /**
   * GUI test override: directly drives output pin HIGH or LOW.
   * Stores override so scheduler's {@code setBlockState()} calls are
   * suppressed until {@link #clearManualOutput} is called.
   *
   * @param pairName pair name
   * @param high     true = drive HIGH, false = drive LOW
   * @throws IOException if pair is unavailable or ioctl call fails
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
   * Clears manual test override for a pair.
   * Scheduler will resume automatic control on its next tick.
   */
  public void clearManualOutput(String pairName) {
    manualOverrides.remove(pairName);
    LOG.infof("GPIO pair '%s': manual override cleared", pairName);
  }

  /**
   * Reads input pin of named pair and returns whether external
   * override switch is active.
   *
   * @throws IOException if pair is unavailable or ioctl call fails
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

  /** Returns full system + per-pair status snapshot. */
  public GpioStatus getStatus() {
    List<GpioPairStatus> pairStatusList = new ArrayList<>();

    for (var entry : gpioConfig.pairs().entrySet()) {
      String name = entry.getKey();
      GpioConfig.GpioPairConfig cfg = entry.getValue();
      GpioPairState state = pairStates.get(name);

      if (state == null) {
        pairStatusList.add(new GpioPairStatus(
          name, false, cfg.outputPin(), null, false,
          cfg.inputPin(), cfg.inputBias(), null, false, null,
          "Pair initialisation failed"
        ));
        continue;
      }

      Boolean outState = null;
      Boolean inState = null;
      boolean extActive = false;
      try {
        outState = getLineValue(state.outputLineFd()) == 1;
      } catch (Throwable ignored) {
        // Pin read failed — report as null
      }
      try {
        int inVal = getLineValue(state.inputLineFd());
        inState = (inVal == 1);
        extActive = "HIGH".equalsIgnoreCase(state.inputActiveLevel())
          ? (inVal == 1) : (inVal == 0);
      } catch (Throwable ignored) {
        // Pin read failed — report as null
      }

      Long assignedDevice = assignmentRepository.findByPairName(name)
        .map(a -> a.deviceId)
        .orElse(null);

      pairStatusList.add(new GpioPairStatus(
        name, true,
        state.outputPin(), outState,
        manualOverrides.containsKey(name),
        state.inputPin(), cfg.inputBias(), inState, extActive,
        assignedDevice, null
      ));
    }

    return new GpioStatus(
      systemAvailable, isRaspberryPi, platform, systemErrorMessage,
      Collections.unmodifiableList(pairStatusList)
    );
  }

  // ========== Platform detection ==========

  private void detectPlatform() {
    try {
      String cpuInfo = Files.readString(Path.of("/proc/cpuinfo"));
      this.isRaspberryPi = cpuInfo.contains("Raspberry Pi");
      this.platform = cpuInfo.lines()
        .filter(line -> line.startsWith("Model"))
        .findFirst()
        .map(line -> line.split(":", 2)[1].trim())
        .orElse("Unknown");
      LOG.infof("Platform detected: %s (RPi=%b)", platform, isRaspberryPi);
    } catch (Exception e) {
      LOG.warnf("Failed to detect platform: %s", e.getMessage());
    }
  }

  // ========== GPIO chip + line management ==========

  private void openChip() throws IOException {
    try {
      MemorySegment chipPath = arena.allocateFrom(gpioConfig.chipDevice());
      this.chipFd = (int) openHandle.invoke(chipPath, O_RDWR);
      if (chipFd < 0) {
        throw new IOException("Failed to open GPIO chip: " + gpioConfig.chipDevice());
      }
      LOG.debugf("Opened GPIO chip fd=%d (%s)", chipFd, gpioConfig.chipDevice());
    } catch (IOException e) {
      throw e;
    } catch (Throwable t) {
      throw new IOException("Failed to open GPIO chip", t);
    }
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
        LOG.infof("GPIO pair '%s' ready: output=%d, input=%d",
          name, cfg.outputPin(), cfg.inputPin());
      } catch (Exception e) {
        LOG.errorf(e, "Failed to initialise GPIO pair '%s': %s — pair unavailable",
          name, e.getMessage());
        // Other pairs continue unaffected
      }
    }
  }

  private int requestLine(int lineOffset, long flags, int defaultValue) throws IOException {
    try {
      // gpio_v2_line_request is 592 bytes (see struct layout in plan)
      MemorySegment req = arena.allocate(592);
      req.fill((byte) 0);

      req.set(ValueLayout.JAVA_INT, 0, lineOffset);                 // offsets[0]

      MemorySegment consumerSeg = req.asSlice(256, 32);              // consumer[32]
      byte[] consumerBytes = gpioConfig.consumerLabel()
        .getBytes(StandardCharsets.UTF_8);
      for (int i = 0; i < Math.min(consumerBytes.length, 31); i++) {
        consumerSeg.set(ValueLayout.JAVA_BYTE, i, consumerBytes[i]);
      }

      req.set(ValueLayout.JAVA_LONG, 288, flags);                   // config.flags
      req.set(ValueLayout.JAVA_INT, 296, 0);                        // config.num_attrs
      req.set(ValueLayout.JAVA_INT, 560, 1);                        // num_lines
      req.set(ValueLayout.JAVA_INT, 564, 0);                        // event_buffer_size

      int result = (int) ioctlHandle.invoke(chipFd, GPIO_V2_GET_LINE_IOCTL, req);
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
    } catch (IOException e) {
      throw e;
    } catch (Throwable t) {
      throw new IOException("Failed to request GPIO line " + lineOffset, t);
    }
  }

  private void setLineValue(int lineFd, int value) throws IOException {
    try {
      MemorySegment vals = arena.allocate(16);
      vals.set(ValueLayout.JAVA_LONG, 0, value != 0 ? 1L : 0L);    // bits
      vals.set(ValueLayout.JAVA_LONG, 8, 1L);                       // mask
      int result = (int) ioctlHandle.invoke(lineFd, GPIO_V2_LINE_SET_VALUES_IOCTL, vals);
      if (result < 0) {
        throw new IOException("ioctl GPIO_V2_LINE_SET_VALUES_IOCTL failed on fd=" + lineFd);
      }
    } catch (IOException e) {
      throw e;
    } catch (Throwable t) {
      throw new IOException("Failed to set GPIO line value on fd=" + lineFd, t);
    }
  }

  private int getLineValue(int lineFd) throws IOException {
    try {
      MemorySegment vals = arena.allocate(16);
      vals.set(ValueLayout.JAVA_LONG, 0, 0L);
      vals.set(ValueLayout.JAVA_LONG, 8, 1L);
      int result = (int) ioctlHandle.invoke(lineFd, GPIO_V2_LINE_GET_VALUES_IOCTL, vals);
      if (result < 0) {
        throw new IOException("ioctl GPIO_V2_LINE_GET_VALUES_IOCTL failed on fd=" + lineFd);
      }
      return (vals.get(ValueLayout.JAVA_LONG, 0) & 1L) != 0 ? 1 : 0;
    } catch (IOException e) {
      throw e;
    } catch (Throwable t) {
      throw new IOException("Failed to get GPIO line value on fd=" + lineFd, t);
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

  // ========== Private helpers ==========

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

  // ========== Cleanup ==========

  private void cleanup() {
    for (GpioPairState state : pairStates.values()) {
      closeFd(state.outputLineFd(), "output line fd for pair '" + state.name() + "'");
      closeFd(state.inputLineFd(), "input line fd for pair '" + state.name() + "'");
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
      closeHandle.invoke(fd);
    } catch (Throwable t) {
      LOG.warnf("Failed to close %s (fd=%d): %s", label, fd, t.getMessage());
    }
  }
}
