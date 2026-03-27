# Security Guidelines for Frodo

## Secret Scanning

This project uses **Gitleaks** to prevent sensitive data from being committed to the repository.

### What is Scanned?

- Passwords and credentials
- API keys and tokens
- Private keys and certificates
- Database connection strings with embedded credentials
- JWT secrets
- High-entropy strings (likely secrets)

### Pre-commit Hook

Every commit is automatically scanned for secrets. If sensitive data is detected:

1. The commit will be **blocked**
2. You'll see a detailed report of the findings
3. Remove the sensitive data before committing again

**Example blocked commit:**
```
❌ Gitleaks detected potential secrets in your commit!

Finding:     firebird-password-production
Secret:      quarkus.datasource.password=MySecretP@ssw0rd123
File:        src/main/resources/application-prod.properties
```

### Manual Scanning

Scan the repository manually:
```bash
./gradlew scanSecrets
```

Or use gitleaks directly:
```bash
gitleaks detect --config .gitleaks.toml --verbose
```

### False Positives

If gitleaks flags something that's not actually a secret (false positive):

1. Review the finding carefully
2. Add it to the allowlist in `.gitleaks.toml`:

```toml
[allowlist]
regexes = [
    '''your-safe-pattern-here''',
]

paths = [
    '''path/to/safe/file\.txt$''',
]
```

### Bypassing the Check (NOT RECOMMENDED)

Only in exceptional cases:
```bash
git commit --no-verify
```

**Warning:** This bypasses security scanning and could leak secrets!

---

## Best Practices

### 1. Never Commit Credentials

**Bad:**
```properties
# application.properties
quarkus.datasource.password=MySecretPassword123
mqtt.broker.password=MqttP@ss
```

**Good:**
```properties
# application.properties
quarkus.datasource.password=${DB_PASSWORD}
mqtt.broker.password=${MQTT_PASSWORD}
```

Then provide via environment variables or external config.

### 2. Use Environment Variables

```bash
export DB_PASSWORD="your-secret-password"
export MQTT_PASSWORD="your-mqtt-password"
./gradlew quarkusDev
```

### 3. Use `.env` Files (Local Only)

Create `.env` files for local development (already gitignored):

```bash
# .env (NOT committed)
DB_PASSWORD=dev-password
MQTT_PASSWORD=dev-mqtt-password
```

Load with:
```bash
source .env
./gradlew quarkusDev
```

### 4. Separate Configuration for Production

Use different configuration files:
- `application.properties` - Default, safe values
- `application-dev.properties` - Development overrides
- `application-prod.properties` - Production (NOT committed, deployed separately)

### 5. Use Secrets Management

For production:
- **Kubernetes Secrets**
- **HashiCorp Vault**
- **AWS Secrets Manager**
- **Azure Key Vault**
- **Environment variables in CI/CD**

---

## Configuration Examples

### Development (Safe to Commit)

```properties
# application.properties
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050/frodo.fdb
quarkus.datasource.username=sysdba
quarkus.datasource.password=masterkey  # Default Firebird password, change in production!

frodo.modbus.device.host=localhost
frodo.modbus.device.port=502
```

### Production (Use Environment Variables)

```properties
# application-prod.properties (deployed separately, NOT committed)
quarkus.datasource.jdbc.url=${DB_URL}
quarkus.datasource.username=${DB_USERNAME}
quarkus.datasource.password=${DB_PASSWORD}

frodo.modbus.device.host=${MODBUS_HOST}
frodo.modbus.device.port=${MODBUS_PORT:502}
```

---

## CI/CD Integration

GitHub Actions automatically scans every push and pull request:

See: `.github/workflows/security-scan.yml`

The build will **fail** if secrets are detected.

---

## Testing with Gitleaks

### Install Gitleaks

**macOS:**
```bash
brew install gitleaks
```

**Linux:**
```bash
# Download from releases
wget https://github.com/gitleaks/gitleaks/releases/download/v8.18.1/gitleaks_8.18.1_linux_x64.tar.gz
tar -xzf gitleaks_8.18.1_linux_x64.tar.gz
sudo mv gitleaks /usr/local/bin/
```

**Windows:**
```powershell
choco install gitleaks
```

### Test the Pre-commit Hook

Try committing a file with a fake secret:
```bash
echo 'api.key=sk_live_51AbCdEfGhIjKlMnOpQrStUv' > test-secret.txt
git add test-secret.txt
git commit -m "Test secret scanning"
# Should be blocked!
```

---

## Incident Response

### If a Secret Was Committed

1. **Immediately revoke/rotate** the exposed credential
2. Remove the secret from git history:
   ```bash
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch path/to/file" \
     --prune-empty --tag-name-filter cat -- --all
   ```
3. Force push (if not on main/master):
   ```bash
   git push origin --force --all
   ```
4. Update the secret everywhere it was used
5. Notify the security team

### Prevention

- Enable branch protection rules
- Require pull request reviews
- Enable secret scanning on GitHub (repository settings)

---

## Questions?

See the [Gitleaks documentation](https://github.com/gitleaks/gitleaks) for more details.
