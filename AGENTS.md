# AGENTS.md - Frodo Modbus Protocol Connector

This file provides guidance for AI coding agents working on this codebase.

## Project Overview

Frodo is a **Quarkus 3.x server application** for Modbus protocol communication with PV (photovoltaic) devices. It collects information and provides control capabilities via REST APIs, MQTT messaging, and a React frontend.

**Key Technologies:**
- Java 21, Quarkus 3.34.x, Gradle (Groovy DSL)
- Modbus TCP protocol (via Vert.x raw sockets + j2mod library)
- MQTT messaging (SmallRye Reactive Messaging)
- FirebirdSQL database (Jaybird JDBC driver)
- React 18 frontend (via Quinoa extension)

**Reference Documentation:** See `refdoc/modbus.pdf` for Modbus protocol specifications.

## Build & Run Commands

```bash
./gradlew quarkusDev          # Development mode with hot reload
./gradlew build               # Build the project
./gradlew clean build         # Clean build
./gradlew build -Dquarkus.container-image.build=true  # Build Docker image
```

## Testing Commands

```bash
./gradlew test                # Run all tests
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest"           # Single test class
./gradlew test --tests "at.or.reder.frodo.modbus.ModbusTcpServiceTest.testBuildMbapFrame"  # Single method
./gradlew test --tests "*ModbusTest*"   # Pattern matching
./gradlew testNative          # Integration tests (native mode)
```

In Quarkus dev mode, press `r` to re-run tests.

## Code Style Guidelines

### Package Structure
```
at.or.reder.frodo/
├── api/        # REST endpoints (JAX-RS resources)
├── health/     # MicroProfile Health checks
├── modbus/     # Modbus TCP service & REST resource
└── mqtt/       # MQTT messaging service
```

### Import Order
1. `io.vertx.*`, `io.smallrye.*`, `io.quarkus.*` (framework imports)
2. `jakarta.*` (Jakarta EE APIs)
3. `org.eclipse.microprofile.*` (MicroProfile APIs)
4. `org.jboss.logging.*` (logging)
5. `java.*` (standard library - last)

Static imports go after regular imports.

### Formatting
- **Indentation:** 2 spaces (no tabs)
- **Encoding:** UTF-8
- **Braces:** Opening brace on same line (K&R style)

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `ModbusTcpService` |
| Methods/Variables | camelCase | `readHoldingRegisters`, `startAddr` |
| Constants | UPPER_SNAKE_CASE | `LOG`, `DEFAULT_PORT` |
| Config properties | kebab-case | `frodo.modbus.host` |
| REST paths | kebab-case | `/api/modbus/{unitId}/holding-registers` |

### Type Conventions
- Use Java **records** for DTOs and response objects
- Use `Uni<T>` for async operations (Mutiny reactive type)
- Use primitive types (`int`, `boolean`) for simple values
- Use `int[]` internally, `List<Integer>` in JSON responses

### CDI & Dependency Injection
```java
@ApplicationScoped
public class ModbusTcpService {
    @Inject
    Vertx vertx;

    @ConfigProperty(name = "frodo.modbus.host", defaultValue = "localhost")
    String modbusHost;
}
```

### REST Resources (JAX-RS)
```java
@Path("/api/modbus")
@Tag(name = "Modbus", description = "Modbus TCP device access endpoints")
public class ModbusResource {
    @GET
    @Path("/{unitId}/holding-registers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Read holding registers")
    public Uni<ModbusRegisterResponse> readHoldingRegisters(...) { }
}
```

### Logging
- Use `org.jboss.logging.Logger` (not SLF4J)
- Static logger: `private static final Logger LOG = Logger.getLogger(ClassName.class);`
- Formatted logging: `LOG.debugf("Message: %s", value);`

### Error Handling
- Throw `IllegalArgumentException` for invalid input
- Use `Uni.onFailure()` for async error handling
- Log errors: `LOG.errorf(exception, "Message: %s", context);`

## Testing Guidelines

| Type | Suffix | Location |
|------|--------|----------|
| Unit tests | `*Test.java` | `src/test/java/` |
| Integration tests | `*IT.java` | `src/native-test/java/` |

### Unit Test Style (pure logic)
```java
class ModbusTcpServiceTest {
    @Test
    void testBuildMbapFrame() {
        byte[] frame = ModbusTcpService.buildMbapFrame(42, 1, pdu);
        assertEquals(12, frame.length);
    }
}
```

### Endpoint Test Style (QuarkusTest + RestAssured)
```java
@QuarkusTest
class FrodoResourceTest {
    @Test
    void testInfoEndpoint() {
        given()
            .when().get("/api/info")
            .then()
            .statusCode(200)
            .body("name", is("frodo"));
    }
}
```

## Project-Specific Patterns

### Modbus Protocol
- MBAP header: Transaction ID (2) + Protocol ID (2) + Length (2) + Unit ID (1)
- Function codes: 0x03 (read holding), 0x06 (write single), 0x10 (write multiple)
- Use static helper methods for frame building/parsing (testable without Vert.x)

### Async/Reactive
- Use Vert.x Mutiny APIs (`io.vertx.mutiny.*`)
- Return `Uni<T>` from async methods
- Chain: `.onItem().transform()`, `.onFailure().invoke()`
- Close resources: `socket.closeAndForget()`

### Configuration Profiles
- `%dev.` prefix for development overrides
- `%test.` prefix for test overrides
- External services disabled in dev/test by default

## Key Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/info` | Application info |
| `GET /api/modbus/{unitId}/holding-registers` | Read Modbus registers |
| `GET /q/health` | Health check |
| `GET /q/metrics` | Prometheus metrics |
| `GET /swagger-ui` | Swagger UI |
