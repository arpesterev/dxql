# Concurrency boundaries

**Status:** The checked-in design has several features compatible with concurrent *read-only* use, but the project does **not** currently provide a verified thread-safety guarantee for arbitrary shared instances.

## What is per invocation

- [`DXQL.executeWholeDocument()`](../src/main/java/ru/council/dxql/DXQL.java) creates request-specific parameter maps, query-result maps and an output `MetaJson` tree.
- [`QueryExecutor.executeQuery()`](../src/main/java/ru/council/dxql/executors/QueryExecutor.java) constructs a fresh `MetaJsonRowMapper` for each query.
- [`SqlExecutor`](../src/main/java/ru/council/dxql/executors/SqlExecutor.java) uses a `TransactionTemplate` configured during construction. Spring documents sharing a `TransactionTemplate` with unchanged settings as thread-safe.
- The Jackson `ObjectMapper` instances are created/configured during construction. Jackson documents reuse as thread-safe after all configuration is finished.

These choices reduce shared per-request mutation. They do **not** prove thread safety for every configuration, extension or database integration.

## Shared mutable parts and limitations

- [`DXQLConfiguration`](../src/main/java/ru/council/dxql/DXQLConfiguration.java) and [`QueryDefinition`](../src/main/java/ru/council/dxql/models/QueryDefinition.java) expose setters and mutable collections. Execution reads and iterates those definitions **without snapshotting them**. Do not change configuration (including nested lists and merger definitions) while requests run.
- The returned `MetaJson` graphs use mutable collections and are **not safe for concurrent writes**. See [metan's concurrency notes](https://github.com/arpesterev/metan/blob/master/docs/CONCURRENCY.md).
- The JDBC data source, transaction manager and all caller-supplied parameter values must satisfy their own concurrent-use contracts.
- `@Transactional` annotations and database transactions **do not automatically serialize application threads** or protect Java object graphs.

## Practical usage

1. Construct and validate the full configuration before serving requests, then treat it and every nested object as immutable.
2. Do not call mutators on a shared `ObjectMapper` or `TransactionTemplate` once requests have started.
3. Keep input maps and return values request-scoped; never update a `MetaJson` shared with another thread without external coordination.
4. Avoid passing a single mutable caller-supplied map or collection to concurrent calls while it may be modified.
5. If shared-instance concurrency is a requirement, write an integration test with a real connection pool and concurrent calls using **different parameter sets**, and verify both transaction-local temporary tables and independent results.

**Conclusion:** `DXQL` is a candidate for concurrent calls with frozen configuration and correctly configured dependencies; do not advertise it as unconditionally thread-safe without a test-backed contract.

References: [Spring TransactionTemplate](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html), [Jackson ObjectMapper 2.13](https://fasterxml.github.io/jackson-databind/javadoc/2.13/com/fasterxml/jackson/databind/ObjectMapper.html).

This note is a code-level review; no parallel integration tests were run.
