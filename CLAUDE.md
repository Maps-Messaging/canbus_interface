# CLAUDE.md - AI Assistant Guidelines

This file provides guidance for AI assistants working with this codebase.

## Project Overview

This is a **CAN bus protocol stack** implemented in Java, providing:
- Native SocketCAN device access (Linux-only)
- J1939 protocol frame construction and parsing
- NMEA 2000 (N2K) message decoding and JSON conversion

The project is a **multi-module Maven build** owned by MapsMessaging B.V.

## Repository Structure

```
canbus_interface/
├── canbus-core/          # SocketCAN device layer (JNA-based)
├── canbus-J1939/         # J1939 CAN identifier utilities
├── canbus-nmea2000/      # NMEA 2000 payload codec
├── pom.xml               # Parent POM
└── .buildkite/           # CI pipeline configuration
```

### Module Dependency Hierarchy

```
canbus-nmea2000
    ├── canbus-J1939
    └── canbus-core
```

### Module Responsibilities

| Module | Purpose |
|--------|---------|
| `canbus-core` | SocketCAN device I/O, CAN frame handling, capability detection |
| `canbus-J1939` | 29-bit CAN identifier parsing/building, PDU1/PDU2 handling |
| `canbus-nmea2000` | N2K XML parsing, PGN decoding, JSON conversion, schema generation |

## Build Commands

```bash
# Standard build with tests
mvn clean install

# Deploy snapshots
mvn clean deploy -Psnapshot

# Release to Maven Central
mvn clean deploy -Prelease

# Run tests only
mvn test

# Skip tests
mvn clean install -DskipTests
```

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Target JDK (release mode) |
| Maven | 3.x | Build tool |
| JNA | 5.18.1 | Native library access for SocketCAN |
| Lombok | 1.18.42 | Boilerplate reduction |
| Gson | 2.13.2 | JSON serialization |
| JUnit Jupiter | 6.0.1 | Testing framework |
| Mockito | 5.21.0 | Mocking framework |

## Package Structure

All source code uses the base package: `io.mapsmessaging.canbus`

```
io.mapsmessaging.canbus
├── app/                    # Application utilities (canbus-core)
├── device/                 # SocketCAN device layer (canbus-core)
│   └── frames/             # CAN frame structures
├── j1939/                  # J1939 protocol (canbus-J1939)
│   └── n2k/                # NMEA 2000 (canbus-nmea2000)
│       ├── codec/          # Bit-level field codecs
│       ├── compile/        # PGN definition compiler
│       ├── framing/        # Fast-packet assembly
│       ├── model/          # Definition data models
│       ├── parser/         # XML dialect parser
│       └── schema/         # JSON schema generation
```

## Code Conventions

### License Headers

All source files must include the Apache 2.0 + Commons Clause license header:

```java
/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  ...
 */
```

### Class Design Patterns

- Use `final` classes where inheritance is not intended
- Use Java records for immutable data (e.g., `CanCapabilities`)
- Use Lombok `@Getter` sparingly, prefer explicit getters for complex logic
- Use package-private constructors for test-only instantiation
- Prefer static factory methods (`parse()`, `build()`) over public constructors

### Naming Conventions

- Classes: PascalCase (e.g., `SocketCanDevice`, `CanIdBuilder`)
- Methods: camelCase, verb-first (e.g., `readFrame()`, `parsePayload()`)
- Constants: SCREAMING_SNAKE_CASE (e.g., `CAN_FD_MAX_PAYLOAD`)
- Test classes: `<ClassName>Test` in same package under `src/test/java`

### Error Handling

- Use `IOException` for device/transport failures
- Use `IllegalArgumentException` for invalid input validation
- Prefer explicit error messages with context (e.g., `"errno=" + errno`)
- No unchecked exceptions should escape public API methods

## Testing Patterns

### Test Structure

- Use JUnit Jupiter (`org.junit.jupiter.api`)
- Use Mockito for mocking native/external dependencies
- Package-private test classes (no `public` modifier)
- Method naming: `methodName_condition_expectedBehavior()`

### Example Test Pattern

```java
@Test
void readFrame_classic_returnsCanFrame() throws Exception {
    LibCFacade libC = mock(LibCFacade.class);
    SocketCanDevice device = newDevice(libC, ...);

    when(libC.read(...)).thenAnswer(...);

    CanFrame frame = device.readFrame();

    Assertions.assertEquals(expected, frame.getCanIdentifier());
}
```

### Test Base Classes

The `canbus-nmea2000` module uses `BaseTest` for shared test utilities:
- Random value generation within field constraints
- Registry building from XML resources
- Field lookup helpers

## Key Classes

### canbus-core

| Class | Purpose |
|-------|---------|
| `SocketCanDevice` | Main device interface for reading/writing CAN frames |
| `CanCapabilities` | Record describing interface FD support and payload limits |
| `CanFrame` | Immutable CAN frame representation |
| `LibCFacade` | Interface for native libc operations (testable abstraction) |

### canbus-J1939

| Class | Purpose |
|-------|---------|
| `CanId` | Parsed 29-bit CAN identifier with PGN, priority, source, destination |
| `CanIdBuilder` | Static utility for building 29-bit identifiers from J1939 fields |

### canbus-nmea2000

| Class | Purpose |
|-------|---------|
| `N2kParserFactory` | Singleton factory for obtaining compiled N2K registry |
| `N2kCompiledRegistry` | Thread-safe registry of compiled PGN definitions |
| `N2kCompiler` | Compiles XML definitions into runtime registry |
| `N2kXmlDialectParser` | Parses CANboat-style XML PGN definitions |

## Design Principles

These principles are explicitly documented in module READMEs:

1. **Explicit layering**: CAN socket -> framing -> protocol -> profile
2. **Protocol correctness first**: Correct J1939/N2K semantics over convenience
3. **No vendor lock-in**: Support for pluggable dialects and definitions
4. **Transport-aware, application-agnostic**: No UI or business logic
5. **Immutability over defensive copying**: Thread-safe by design
6. **Explicit errors over silent corruption**: Always report failures

## CI/CD (Buildkite)

The project uses Buildkite for CI:

- **pipeline.yml**: Build, test, deploy snapshots, run SonarCloud analysis
- **pipeline_release.yml**: Release to Maven Central

### Build Agents

Builds run on queue: `java_build_queue`

### Environment Variables

- `SONAR_TOKEN`: SonarCloud authentication
- `NVD_API_KEY`: OWASP dependency check API key

## Platform Requirements

- **Linux only**: SocketCAN requires Linux kernel with CAN support
- **CAN interface**: Physical or virtual CAN device (e.g., `can0`, `vcan0`)
- **Java 17+**: Required for compilation and runtime

## Common Tasks

### Adding a New PGN Definition

1. Add definition to `canbus-nmea2000/src/main/resources/n2k/NMEA_database_1_300.xml`
2. Follow existing XML schema for fields, bit offsets, and types
3. Add round-trip test in `N2kRoundTripAllPgnsTest`

### Testing with Virtual CAN

```bash
# Create virtual CAN interface
sudo modprobe vcan
sudo ip link add dev vcan0 type vcan
sudo ip link set up vcan0

# Now run tests or application against vcan0
```

### Adding a New Module

1. Create directory `canbus-<name>/`
2. Add `pom.xml` with parent reference
3. Add module to parent `pom.xml` `<modules>` section
4. Follow existing package structure under `io.mapsmessaging.canbus`

## Things to Avoid

- Do not add transport/framing logic to `canbus-core` (belongs in protocol modules)
- Do not add vendor-specific PGN handling to core NMEA 2000 module
- Do not use platform-specific code outside of `canbus-core`
- Do not add UI or application-level code to any module
- Do not hardcode PGN definitions (use XML-driven approach)

## Resources

- [SocketCAN documentation](https://www.kernel.org/doc/html/latest/networking/can.html)
- [J1939 standard overview](https://www.sae.org/standards/content/j1939_201408/)
- [CANboat project](https://github.com/canboat/canboat) (source of N2K XML definitions)
