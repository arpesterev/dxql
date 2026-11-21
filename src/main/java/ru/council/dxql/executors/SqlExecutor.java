package ru.council.dxql.executors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.council.dxql.DXQLConfiguration;
import ru.council.dxql.models.ConstantParameter;
import ru.council.metan.jdbc.MetaJsonRowMapper;
import ru.council.metan.models.MetaJson;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SqlExecutor {

    protected final DXQLConfiguration configuration;
    protected final DataSourceTransactionManager dataSourceTransactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NamedParameterJdbcTemplate namedTemplate;
    @Getter
    private final TransactionTemplate transactionTemplate;

    public SqlExecutor(JdbcTemplate template, DXQLConfiguration configuration) {
        Objects.requireNonNull(template.getDataSource());
        this.namedTemplate = new NamedParameterJdbcTemplate(template);
        this.configuration = Objects.requireNonNull(configuration, "configuration").detachedCopy();
        dataSourceTransactionManager = new DataSourceTransactionManager();
        dataSourceTransactionManager.setDataSource(namedTemplate.getJdbcTemplate().getDataSource());
        transactionTemplate = new TransactionTemplate(dataSourceTransactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Return a detached snapshot so callers cannot change the configuration
     * used by concurrent executions through this getter.
     */
    public DXQLConfiguration getConfiguration() {
        return configuration.detachedCopy();
    }

    /**
     * Apply PostgreSQL session options only for the current transaction.
     *
     * Schema and locale are passed as values, never concatenated into SQL.
     * quote_ident makes search_path a single identifier, not a list of schemas.
     * Call this from inside TransactionTemplate.execute().
     */
    protected void configureTransactionSession(String schema, String locale) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(locale, "locale");
        if (schema.isBlank() || locale.isBlank()) {
            throw new IllegalArgumentException("Schema and locale must not be blank");
        }
        namedTemplate.queryForObject(
                "SELECT pg_catalog.set_config('search_path', pg_catalog.quote_ident(:schema), true)",
                Map.of("schema", schema),
                String.class);
        namedTemplate.queryForObject(
                "SELECT pg_catalog.set_config('lc_time', :locale, true)",
                Map.of("locale", locale),
                String.class);
    }

    protected void execute(String sql) {
        namedTemplate.execute(sql, PreparedStatement::execute);
    }

    protected void execute(String sql, Map<String, Object> params) {
        namedTemplate.execute(sql, params, PreparedStatement::execute);
    }

    private MetaJson postProcessRowMapper(MetaJsonRowMapper rowMapper) {
        return rowMapper.getJson();
    }

    protected MetaJson executeSQL(String sql, MetaJsonRowMapper rowMapper) {
        namedTemplate.query(sql, rowMapper);

        return postProcessRowMapper(rowMapper);
    }

    protected MetaJson executeSQL(String sql, Map<String, Object> params, MetaJsonRowMapper rowMapper) {
        namedTemplate.query(sql, params, rowMapper);

        return postProcessRowMapper(rowMapper);
    }

    protected Map<String, Object> getConstantParametersMap(List<ConstantParameter> constantParameters) throws JsonProcessingException {
        Map<String, Object> result = new HashMap<>();

        for (ConstantParameter parameter : constantParameters) {
            result.put(parameter.getName(), objectMapper.readValue(parameter.getValue(), parameter.getType().getTypeClass()));
        }

        return result;
    }

}
