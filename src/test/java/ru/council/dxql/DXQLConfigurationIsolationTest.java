package ru.council.dxql;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.council.dxql.models.SelectDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DXQLConfigurationIsolationTest {

    private static DXQLConfiguration sample() {
        DXQLConfiguration config = new DXQLConfiguration();
        config.setSchema("tenant_initial");
        SelectDefinition definition = new SelectDefinition();
        definition.setQueryIdentifier("select_one");
        definition.setQuery("SELECT 1");
        config.getSelectDefinitions().add(definition);
        return config;
    }

    @Test
    void snapshotDoesNotShareNestedConfiguration() {
        DXQLConfiguration original = sample();
        DXQLConfiguration snapshot = original.detachedCopy();
        original.setSchema("tenant_changed");
        original.getSelectDefinitions().get(0).setQuery("SELECT 2");
        original.getSelectDefinitions().clear();

        assertEquals("tenant_initial", snapshot.getSchema());
        assertEquals(1, snapshot.getSelectDefinitions().size());
        assertEquals("SELECT 1", snapshot.getSelectDefinitions().get(0).getQuery());
    }

    @Test
    void executorDoesNotExposeLiveConfiguration() throws Exception {
        DXQLConfiguration original = sample();
        // The constructor only needs a non-null DataSource, not a live database connection.
        DXQL executor = new DXQL(
                new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://localhost/unused")),
                original);

        original.setSchema("caller_changed");
        original.getSelectDefinitions().clear();
        assertEquals("tenant_initial", executor.getConfiguration().getSchema());
        assertEquals(1, executor.getConfiguration().getSelectDefinitions().size());

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                tasks.add(pool.submit(() -> {
                    DXQLConfiguration visible = executor.getConfiguration();
                    visible.setSchema("thread_local");
                    visible.getSelectDefinitions().get(0).setQuery("SELECT 9");
                    assertEquals("tenant_initial", executor.getConfiguration().getSchema());
                    assertEquals("SELECT 1",
                            executor.getConfiguration().getSelectDefinitions().get(0).getQuery());
                }));
            }
            for (Future<?> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
