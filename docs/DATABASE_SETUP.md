# Firebird Database Setup

This document describes how to create and configure the Firebird database for Frodo.

## Prerequisites

Choose one of the following:

### Option A: Docker (Recommended for Development)
- Docker 20.10+ installed
- Docker Compose 2.0+ (optional, for easier management)

### Option B: Native Installation
- Firebird 5.0+ installed
- `isql` command-line tool available
- SYSDBA credentials (default: sysdba/masterkey)

## Database Creation

### Method 1: Docker Container (Recommended for Development)

#### Using Docker Run

Pull and run the official Firebird Docker image:

```bash
docker run -d \
  --name frodo-firebird \
  -e ISC_PASSWORD=masterkey \
  -e FIREBIRD_DATABASE=frodo.fdb \
  -e FIREBIRD_USER=sysdba \
  -p 3050:3050 \
  -v frodo-firebird-data:/firebird/data \
  firebirdsql/firebird:5.0
```

**Environment Variables:**
- `ISC_PASSWORD` - SYSDBA password (default: masterkey)
- `FIREBIRD_DATABASE` - Database name (creates with default settings)
- `FIREBIRD_USER` - Database user (optional, defaults to SYSDBA)

**Important**: The auto-created database uses default settings (8K page size, NONE charset). For production, create the database manually with UTF-8 and 32K page size.

#### Create Database with UTF-8 and 32K Page Size in Docker

After starting the container, create the database with proper settings:

```bash
# Copy creation script to container
docker cp src/main/resources/db/create-database.sql frodo-firebird:/tmp/

# Execute script inside container
docker exec -it frodo-firebird isql -user sysdba -password masterkey -input /tmp/create-database.sql

# Or create interactively
docker exec -it frodo-firebird isql -user sysdba -password masterkey
```

Then run:
```sql
CREATE DATABASE 'frodo'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
QUIT;
```

#### Using Docker Compose (Recommended)

Create `docker-compose.yml` in project root:

```yaml
version: '3.8'

services:
  frodo-firebird:
    image: firebirdsql/firebird:5.0
    container_name: frodo-firebird
    environment:
      ISC_PASSWORD: masterkey
      # Don't set FIREBIRD_DATABASE - we'll create manually with UTF-8
    ports:
      - "3050:3050"
    volumes:
      - firebird-data:/firebird/data
      - ./src/main/resources/db/create-database.sql:/docker-entrypoint-initdb.d/create-database.sql:ro
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "/usr/local/firebird/bin/isql", "-user", "sysdba", "-password", "masterkey", "/firebird/data/frodo.fdb", "-q", "-z"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

volumes:
  firebird-data:
    driver: local
```

Start the database:

```bash
# Start Firebird container
docker compose up -d

# Wait for container to be ready (watch logs)
docker compose logs -f frodo-firebird

# Create database with UTF-8 and 32K page size
docker compose exec frodo-firebird isql -user sysdba -password masterkey << 'EOF'
CREATE DATABASE '/firebird/data/frodo.fdb' USER 'sysdba' PASSWORD 'masterkey' PAGE_SIZE 32768  DEFAULT CHARACTER SET UTF8;
QUIT;
EOF

# Verify database
docker compose exec frodo-firebird isql -user sysdba -password masterkey /firebird/data/frodo.fdb -q << 'EOF'
SELECT MON$PAGE_SIZE AS PAGE_SIZE, RDB$CHARACTER_SET_NAME AS DEFAULT_CHARSET
FROM MON$DATABASE CROSS JOIN RDB$DATABASE;
EOF
```

#### Docker Connection String

Update `application.properties` for Docker:

```properties
# Docker Firebird (localhost:3050)
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050//firebird/data/frodo.fdb?encoding=UTF8&charSet=utf-8

# Or use container name if running app in Docker network
quarkus.datasource.jdbc.url=jdbc:firebirdsql://frodo-firebird:3050//firebird/data/frodo.fdb?encoding=UTF8&charSet=utf-8
```

**Note**: Double slash `//` before `/firebird/data/` indicates absolute path inside container.

#### Managing Docker Firebird

```bash
# Start container
docker-compose up -d

# Stop container
docker-compose stop

# View logs
docker-compose logs -f firebird

# Access isql shell
docker-compose exec firebird isql -user sysdba -password masterkey /firebird/data/frodo.fdb

# Backup database
docker-compose exec firebird gbak -b -user sysdba -password masterkey /firebird/data/frodo.fdb /firebird/data/frodo-backup.fbk

# Copy backup out of container
docker cp frodo-firebird:/firebird/data/frodo-backup.fbk ./frodo-backup.fbk

# Restore database
docker-compose exec firebird gbak -c -user sysdba -password masterkey /firebird/data/frodo-backup.fbk /firebird/data/frodo-restored.fdb

# Remove container and volumes (WARNING: deletes all data!)
docker-compose down -v
```

### Method 2: Native Installation

#### 1. Create Database with UTF-8 Character Set and 32K Page Size

**Important**: The database must use UTF-8 character set (UTF8) and 32K page size for optimal performance with large VARCHAR columns.

##### Option A: Using isql Command Line

```bash
isql -user sysdba -password masterkey

# Then run:
CREATE DATABASE 'localhost:frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';

QUIT;
```

##### Option B: Using isql with SQL File

Create a file `create-database.sql`:

```sql
CREATE DATABASE 'localhost:frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
```

Then execute:

```bash
isql -user sysdba -password masterkey -input create-database.sql
```

##### Option C: Using JDBC Connection String (Auto-create)

The Firebird JDBC driver can auto-create the database if it doesn't exist by adding `createDatabaseIfNotExist=true` to the connection string:

```
jdbc:firebirdsql://localhost:3050/frodo.fdb?createDatabaseIfNotExist=true&encoding=UTF8&charSet=utf-8
```

**Note**: Page size cannot be specified via JDBC connection string, so Option A or B is recommended.

#### 2. Verify Database Configuration

After creation, verify the database configuration:

```bash
isql -user sysdba -password masterkey localhost:frodo.fdb

# Check page size and character set:
SELECT
  MON$PAGE_SIZE AS PAGE_SIZE,
  RDB$CHARACTER_SET_NAME AS DEFAULT_CHARSET
FROM MON$DATABASE
CROSS JOIN RDB$DATABASE;
```

Expected output:
```
PAGE_SIZE  DEFAULT_CHARSET
=========  ===============
32768      UTF8
```

## Connection String Configuration

### Production (application.properties)

```properties
# FirebirdSQL Datasource
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050/frodo.fdb?encoding=UTF8&charSet=utf-8
quarkus.datasource.username=sysdba
quarkus.datasource.password=masterkey
```

### Character Set Parameters

- `encoding=UTF8` - Sets the connection character set to UTF8
- `charSet=utf-8` - Java charset name for String encoding/decoding

Both parameters ensure proper UTF-8 handling throughout the JDBC layer.

## Schema Migration

After database creation, Liquibase will automatically create all tables and indexes on first startup:

```bash
./gradlew quarkusRun
```

Liquibase changelogs are located in:
- `src/main/resources/db/changelog/db.changelog-master.xml` (master file)
- `src/main/resources/db/changelog/v1.0.0-create-modbus-tables.xml` (table definitions)

## Manual Schema Creation (Alternative)

If you prefer to create the schema manually without Liquibase:

1. Disable Liquibase: `quarkus.liquibase.enabled=false`
2. Export SQL from Liquibase:
   ```bash
   ./gradlew liquibaseUpdate --liquibaseCommand=updateSQL > schema.sql
   ```
3. Execute SQL manually:
   ```bash
   isql -user sysdba -password masterkey localhost:frodo.fdb -input schema.sql
   ```

## Database Maintenance

### Backup

```bash
gbak -b -user sysdba -password masterkey localhost:frodo.fdb frodo-backup.fbk
```

### Restore

```bash
gbak -c -user sysdba -password masterkey frodo-backup.fbk localhost:frodo-restored.fdb
```

### Validate Database

```bash
gfix -v -full -user sysdba -password masterkey localhost:frodo.fdb
```

## Troubleshooting

### Connection Refused

**Error**: `org.firebirdsql.jdbc.FBSQLException: I/O error for file createDatabaseIfNotExist`

**Solution**: Ensure Firebird server is running:
```bash
# Linux
systemctl status firebird
sudo systemctl start firebird

# Check if port 3050 is listening
netstat -tlnp | grep 3050
```

### Character Set Mismatch

**Error**: `Cannot transliterate character between character sets`

**Solution**: Ensure both database and connection use UTF8:
1. Check database charset: `SELECT RDB$CHARACTER_SET_NAME FROM RDB$DATABASE`
2. Verify JDBC URL includes: `encoding=UTF8&charSet=utf-8`

### Page Size Too Small

**Symptom**: Performance issues with large VARCHAR columns

**Solution**: Recreate database with 32K page size (see above). Cannot be changed after creation.

## References

- [Firebird 5.0 Documentation](https://firebirdsql.org/file/documentation/html/en/firebirddocs/firebird-docset.html)
- [Jaybird JDBC Driver](https://firebirdsql.org/en/jdbc-driver/)
- [Liquibase Firebird Support](https://docs.liquibase.com/start/tutorials/firebird.html)
