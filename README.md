# frodo

Frodo is a Quarkus 3.x server application built with Gradle (Groovy DSL) and Java 21.

## Features

| Feature | Technology |
|---------|-----------|
| REST API | Quarkus REST + Jackson |
| API Documentation | SmallRye OpenAPI / Swagger UI |
| Monitoring | Micrometer with Prometheus registry (JVM, App, OS metrics) |
| Messaging | MQTT via SmallRye Reactive Messaging |
| Raw TCP / Modbus | Vert.x NetClient (Modbus TCP protocol, FC 03/06/16) |
| Database | FirebirdSQL via Jaybird 6 JDBC driver + Agroal connection pool |
| Containerization | Docker (Quarkus Docker extension) |
| Frontend UI | React 18 (via Quinoa extension) |

## Prerequisites

- Java 21+
- Docker (for container builds and Firebird database)
- Node.js 20+ (for the React UI; installed automatically by Quinoa)
- FirebirdSQL 5.0+ (for production; Docker recommended for development)

## Database Setup

Frodo uses FirebirdSQL as the production database. The database must be created with **UTF-8 character set** and **32K page size**.

### Quick Start with Docker (Recommended)

```bash
# Option 1: Automated setup script
./scripts/setup-firebird-docker.sh

# Option 2: Manual Docker Compose
docker-compose up -d firebird
docker-compose exec firebird isql -user sysdba -password masterkey << EOF
CREATE DATABASE '/firebird/data/frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';
EOF
```

### Manual Setup (Native Installation)

```bash
# Create database with isql
isql -user sysdba -password masterkey -input src/main/resources/db/create-database.sql
```

**Detailed Instructions**: See **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)** for:
- Docker setup with docker-compose
- Native Firebird installation
- Connection configuration
- Backup/restore procedures
- Troubleshooting

**Note**: Dev and test modes do not require a database (Hibernate and datasource are disabled).

## Running in Development Mode

```bash
./gradlew quarkusDev
```

The application starts on <http://localhost:8080>.

| Endpoint | URL |
|----------|-----|
| React UI | <http://localhost:8080/> |
| Swagger UI | <http://localhost:8080/swagger-ui> |
| OpenAPI Spec | <http://localhost:8080/q/openapi> |
| Prometheus Metrics | <http://localhost:8080/q/metrics> |
| Health Check | <http://localhost:8080/q/health> |
| Modbus API | <http://localhost:8080/api/modbus/{unitId}/holding-registers?start=0&count=10> |

## Building

```bash
./gradlew build
```

## Building a Docker Image

```bash
./gradlew build -Dquarkus.container-image.build=true
```

## Configuration

Key configuration properties in `src/main/resources/application.properties`:

```properties
# FirebirdSQL - Docker (UTF-8 with 32K page size required)
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050//firebird/data/frodo.fdb?encoding=UTF8&charSet=utf-8

# FirebirdSQL - Native installation
# quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050/frodo.fdb?encoding=UTF8&charSet=utf-8

quarkus.datasource.username=sysdba
quarkus.datasource.password=masterkey

# MQTT broker
mp.messaging.incoming.frodo-in.host=localhost
mp.messaging.outgoing.frodo-out.host=localhost

# Modbus TCP device (deprecated, use database configuration)
frodo.modbus.host=localhost
frodo.modbus.port=502
frodo.modbus.enabled=false
```

**Note**: Use double slash `//` for absolute paths in Docker (`//firebird/data/`), single slash for native installations.

See **[docs/DATABASE_SETUP.md](docs/DATABASE_SETUP.md)** for database creation and configuration details.

## Project Structure

```
src/
├── main/
│   ├── java/at/or/reder/frodo/
│   │   ├── api/          – REST endpoints (FrodoResource)
│   │   ├── health/       – Health checks (FrodoHealthCheck)
│   │   ├── modbus/       – Modbus TCP service & REST resource
│   │   └── mqtt/         – MQTT publish/consume service
│   ├── resources/
│   │   └── application.properties
│   ├── docker/           – Dockerfile variants (jvm, native, …)
│   └── webui/            – React 18 frontend (built by Quinoa)
└── test/
    └── java/at/or/reder/frodo/
        └── GreetingResourceTest.java
```
