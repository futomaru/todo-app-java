# todo-app-java

A learning project for building a Todo REST API with Java, Spring Boot, and Gradle.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.x / Spring Framework 7.0 |
| DB access | MyBatis |
| Database | H2 (file-based) |
| Build | Gradle 9 |

## Architecture

```
TodoController → TodoService → TodoMapper → H2
```

## Getting Started

**Requires:** JDK 25

```bash
./gradlew bootRun
```

The application starts at <http://localhost:8080>.

## Build & Test

```bash
./gradlew build   # compile and package
./gradlew test    # run tests
```

## Project Layout

```
src/
  main/
    java/       # Application source (controller, service, mapper, entity, dto, exception)
    resources/  # application.properties, schema.sql, static assets
  test/
    java/       # Unit and integration tests
doc/
  api.yaml            # OpenAPI specification
  architecture.md     # Architecture guidelines
  design.md           # Design decisions
  coding-standards.md # Coding conventions
  testing-guide.md    # Testing approach
  CONTRIBUTING.md     # Contribution guide
```

## Documentation

- [Architecture](doc/architecture.md)
- [Design](doc/design.md)
- [API Spec](doc/api.yaml)
- [Coding Standards](doc/coding-standards.md)
- [Testing Guide](doc/testing-guide.md)
- [Contributing](doc/CONTRIBUTING.md)
