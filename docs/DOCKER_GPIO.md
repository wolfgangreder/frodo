# Docker GPIO Access for Raspberry Pi 5

## Overview

Frodo's GPIO export control uses Linux GPIO character device API (`/dev/gpiochipN`)
via JDK Foreign Function & Memory API. When running inside a Docker container,
the GPIO device and platform detection require explicit configuration.

**Key challenges in Docker:**
- `/dev/gpiochip0` is not visible inside containers by default
- `/proc/cpuinfo` inside a container does not reflect host hardware
- Non-root container user needs GPIO group membership

## Prerequisites

### 1. Check GPIO Device Permissions

On your RPi5 host:

```bash
ls -l /dev/gpiochip0
# Expected: crw-rw---- 1 root gpio 254, 0 ...
```

Note the group name (usually `gpio`) and find its GID:

```bash
getent group gpio | cut -d: -f3
# Example output: 997
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env and set GPIO_GROUP_ID to your system's value
```

### 3. Host User Permissions

If running Docker as non-root, ensure your user is in the GPIO group:

```bash
sudo usermod -aG gpio $USER
# Log out and back in
```

## Running with GPIO Support

### Start with GPIO Profile

```bash
docker compose --profile gpio up -d
```

This starts:
- `firebird` — database
- `frodo-gpio` — Frodo with GPIO device access

### Stop

```bash
docker compose --profile gpio down
```

### View Logs

```bash
docker compose --profile gpio logs -f frodo-gpio
```

## Configuration

GPIO settings are configured via environment variables in `docker-compose.yml`.
SmallRye Config maps `frodo.gpio.*` properties to `FRODO_GPIO_*` env vars.

### Required Settings

| Variable | Default | Description |
|----------|---------|-------------|
| `FRODO_GPIO_ENABLED` | `false` | Enable GPIO subsystem |
| `FRODO_GPIO_FORCE_PLATFORM` | `false` | Skip `/proc/cpuinfo` check |
| `FRODO_GPIO_CHIP_DEVICE` | `/dev/gpiochip0` | GPIO character device path |

### GPIO Pair Configuration

Each pair needs output + input pin numbers (BCM numbering):

```yaml
environment:
  FRODO_GPIO_PAIRS_RELAY1_OUTPUT_PIN: "17"
  FRODO_GPIO_PAIRS_RELAY1_OUTPUT_BLOCK_LEVEL: "HIGH"
  FRODO_GPIO_PAIRS_RELAY1_INPUT_PIN: "27"
  FRODO_GPIO_PAIRS_RELAY1_INPUT_ACTIVE_LEVEL: "HIGH"
  FRODO_GPIO_PAIRS_RELAY1_INPUT_BIAS: "PULL_DOWN"
```

See `application.properties` for all available GPIO properties.

### Docker Compose Settings

| Setting | Purpose |
|---------|---------|
| `devices: [/dev/gpiochip0:/dev/gpiochip0]` | Mount GPIO device into container |
| `group_add: ["${GPIO_GROUP_ID:-997}"]` | Add container process to GPIO group |
| `profiles: [gpio]` | Only starts with `--profile gpio` |

## Troubleshooting

### Permission Denied on /dev/gpiochip0

**Symptom:** `Failed to open GPIO chip: /dev/gpiochip0`

**Check:**

1. Verify `GPIO_GROUP_ID` in `.env` matches host:
   ```bash
   getent group gpio | cut -d: -f3
   ```

2. Verify device is mounted inside container:
   ```bash
   docker compose --profile gpio exec frodo-gpio ls -l /dev/gpiochip0
   ```

3. Verify container process has correct group:
   ```bash
   docker compose --profile gpio exec frodo-gpio id
   ```

**Fallback (less secure):** Add `privileged: true` to frodo-gpio service in `docker-compose.yml`.

### "Not running on Raspberry Pi"

**Symptom:** GPIO init skipped, logs show platform detection failed.

**Fix:** Ensure `FRODO_GPIO_FORCE_PLATFORM: "true"` is set. This skips `/proc/cpuinfo`
and assumes Raspberry Pi.

### GPIO Pairs Not Initialising

**Check:**
1. Correct BCM pin numbers (not physical board pin numbers)
2. Pins not in use by other processes on host
3. Device access working (see permission denied section above)
4. Health endpoint: `curl http://localhost:8082/q/health/ready | jq`
5. GPIO status: `curl http://localhost:8082/api/gpio/status | jq`

### Multiple GPIO Chips

If your device uses a different chip (e.g. `/dev/gpiochip4` on some RPi5 configurations):

1. Update `devices` mapping in `docker-compose.yml`:
   ```yaml
   devices:
     - /dev/gpiochip4:/dev/gpiochip4
   ```
2. Set `FRODO_GPIO_CHIP_DEVICE: /dev/gpiochip4`

## Security Considerations

### group_add (Recommended)

Current setup uses `group_add` to grant GPIO access without elevated privileges:
- Container remains unprivileged
- Only GPIO device is accessible
- No access to other host devices or filesystem

### privileged Mode (Avoid)

`privileged: true` grants full host access. Only use as last resort:
- Full access to all host devices
- Can modify host kernel parameters
- Significant security risk

### Device Isolation

With the default configuration, the container only has access to:
- `/dev/gpiochip0` (explicitly mounted)
- Network access to Firebird database
- No other host devices or filesystem
