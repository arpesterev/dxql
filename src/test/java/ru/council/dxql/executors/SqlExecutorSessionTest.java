package ru.council.dxql.executors;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.council.dxql.DXQLConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SqlExecutorSessionTest {
    private static final String URL_VARIABLE = "DXQL_TEST_PG_URL";

    private static class TestExecutor extends SqlExecutor {
        private final JdbcTemplate jdbc;

        TestExecutor(JdbcTemplate jdbc) {
            super(jdbc, new DXQLConfiguration());
            this.jdbc = jdbc;
        }

        String settingInTransaction(String schema, String locale) {
            return getTransactionTemplate().execute(status -> {
                configureTransactionSession(schema, locale);
                assertEquals(locale, jdbc.queryForObject(
                        "SELECT pg_catalog.current_setting('lc_time')", String.class));
                return jdbc.queryForObject(
                        "SELECT pg_catalog.current_schema()", String.class);
            });
        }
    }

    private TestExecutor executor() {
        String url = System.getenv(URL_VARIABLE);
        assumeTrue(url != null && !url.isBlank(), "Set " + URL_VARIABLE + " for PostgreSQL integration tests");
        DriverManagerDataSource source = new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("DXQL_TEST_PG_USER", "dxql_test"),
                System.getenv().getOrDefault("DXQL_TEST_PG_PASSWORD", "local_test_only"));
        return new TestExecutor(new JdbcTemplate(source));
    }

    @Test
    void schemaAndLocaleAreBoundAsValues() {
        TestExecutor executor = executor();
        assertEquals("public", executor.settingInTransaction("public", "C"));
        // An SQL fragment in a schema name is a *single identifier*, not executable SQL.
        assertNull(executor.settingInTransaction("public'; SELECT 1; --", "C"));
        assertEquals("pg_catalog", executor.settingInTransaction("pg_catalog", "C"));
    }

    @Test
    void concurrentTransactionsUseIndependentSearchPaths() throws Exception {
        TestExecutor executor = executor();
        int count = 48;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; ++i) {
                String selected = i % 2 == 0 ? "public" : "pg_catalog";
                futures.add(pool.submit(() -> {
                    go.await();
                    assertEquals(selected, executor.settingInTransaction(selected, "C"));
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get(40, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
