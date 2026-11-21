# Concurrency boundaries

DXQL can use a shared executor **only when its external dependencies are safe for concurrent use**. The executor now copies its XML configuration at construction and returns detached copies from `getConfiguration()`. This prevents accidental configuration changes by callers through the original object or getter from affecting active executions.

## Configuration ownership

[`DXQLConfiguration.detachedCopy()`](../src/main/java/ru/council/dxql/DXQLConfiguration.java) marshals and unmarshals a configuration, including nested JAXB-bound definitions. [`SqlExecutor`](../src/main/java/ru/council/dxql/executors/SqlExecutor.java) uses a detached copy during construction, and its getter returns another copy.

These changes isolate the executor from normal caller-side mutations, but protected members and subclass extensions can still violate that boundary. They do not make arbitrary configuration objects immutable.

## What is already request-scoped

- [`DXQL.executeWholeDocument()`](../src/main/java/ru/council/dxql/DXQL.java) builds local parameter maps, query result maps and a new result graph.
- [`QueryExecutor.executeQuery()`](../src/main/java/ru/council/dxql/executors/QueryExecutor.java) allocates a fresh `MetaJsonRowMapper` for each query.
- `TransactionTemplate` and Jackson `ObjectMapper` can be reused concurrently after their settings have been finalized, but callers must not reconfigure them during execution.

## Integration requirements

1. Do not mutate the executor's internal protected state from subclasses after initialization.
2. Use a correctly configured JDBC connection pool and transaction manager.
3. Keep parameter maps request-scoped, and do not change the objects held in them during execution.
4. Treat returned `MetaJson` results as caller-owned. For deliberate concurrent mutation, use [metan's synchronized snapshot API](https://github.com/arpesterev/metan/blob/master/docs/CONCURRENCY.md).
5. Transactions do not automatically serialize Java threads. Use database locking or versioned updates for cross-request logical conflicts.

## Verification

[`DXQLConfigurationIsolationTest`](../src/test/java/ru/council/dxql/DXQLConfigurationIsolationTest.java) checks isolation of nested definitions and concurrent access to getter snapshots. It does not cover concurrent SQL execution. A full integration guarantee still requires parallel queries with independent input sets, a real connection pool, PostgreSQL session settings and temporary tables.

This is a narrower guarantee than claiming every DXQL instance is thread-safe in every integration.
