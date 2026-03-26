# frodo

Frodo is a Quarkus 3.x server application built with Gradle (Kotlin DSL) and Java 21.

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
- Docker (for container builds)
- Node.js 20+ (for the React UI; installed automatically by Quinoa)

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
# FirebirdSQL
quarkus.datasource.jdbc.url=jdbc:firebirdsql://localhost:3050/frodo.fdb
quarkus.datasource.username=sysdba
quarkus.datasource.password=masterkey

# MQTT broker
mp.messaging.incoming.frodo-in.host=localhost
mp.messaging.outgoing.frodo-out.host=localhost

# Modbus TCP device
frodo.modbus.host=localhost
frodo.modbus.port=502
frodo.modbus.enabled=false
```

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
