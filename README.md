# DXQL

DXQL is a Java library for running SQL queries described by an XML configuration. It uses Spring JDBC to execute queries and a structured model to validate input parameters and assemble results.

The repository contains the library source and generated API documentation. It is not a standalone database server or a command-line client.

## What is implemented

- **Configuration:** `DXQLConfiguration` loads XML definitions for queries, templates, input variables, temporary tables, schema and locale.
- **Execution:** `DXQL` and the classes under `executors/` run queries through Spring's `JdbcTemplate`.
- **Validation:** parameter types, required values, defaults and constraints are checked before execution.
- **Result processing:** the library uses `metan` to represent and merge structured JSON-like results.

The public entry points are `ru.council.dxql.DXQL` and `ru.council.dxql.DXQLConfiguration`.

## Integration outline

1. Provide a configured Spring `JdbcTemplate` and a database connection.
2. Prepare an XML configuration matching the `DXQLConfiguration` model.
3. Load it with `DXQLConfiguration.from(path)`.
4. Construct `new DXQL(jdbcTemplate, configuration)` and use the query execution API for your configuration.

This is an integration outline, not a complete runnable example. Query definitions and database setup depend on the consuming application.

## Build and documentation

The project uses Gradle, Java, Kotlin support and Dokka. From the repository root, with Gradle and the required JDK installed:

```sh
gradle build
gradle buildDocs
```

The generated API reference is checked into [`docs/dokka/`](docs/dokka/). The Gradle build declares a dependency on `ru.council.libs:metan` from a separate Maven registry. Resolving it may require access to that registry and the `LIBS_PACKAGES_READ_TOKEN` environment variable. No credentials are included here.

## Source map

- `src/main/java/ru/council/dxql/DXQL.java`: main entry point and parameter handling.
- `src/main/java/ru/council/dxql/DXQLConfiguration.java`: XML configuration and validation.
- `src/main/java/ru/council/dxql/executors/`: query execution.
- `src/main/java/ru/council/dxql/models/`: query, parameter and constraint models.

**Build status:** these commands are based on the checked-in Gradle configuration; they have not been verified against the external Maven registry in this documentation update.
