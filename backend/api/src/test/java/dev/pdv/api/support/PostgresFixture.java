package dev.pdv.api.support;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Own UUID namespaces on the real, externally supplied PostgreSQL database. */
public final class PostgresFixture implements AutoCloseable {
    private final String application = "pdv_it_" + UUID.randomUUID().toString().replace("-", "");
    private final String history = application + "_history";

    public Flyway flyway() {
        return configured(null);
    }

    public Flyway configured(String target) {
        var configuration = Flyway.configure()
                .dataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD"))
                .defaultSchema(history).schemas(history).locations("classpath:db/migration")
                .placeholders(Map.of("applicationSchema", application))
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateMigrationNaming(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    public JdbcTemplate jdbc() {
        var source = new DriverManagerDataSource(required("PDV_DB_URL"),
                required("PDV_DB_USER"), required("PDV_DB_PASSWORD"));
        source.setSchema(application);
        return new JdbcTemplate(source);
    }

    @Override
    public void close() throws Exception {
        if (!application.matches("pdv_it_[a-f0-9]{32}") || !history.equals(application + "_history")) {
            throw new IllegalStateException("Cleanup outside owned namespaces is forbidden");
        }
        try (var connection = DriverManager.getConnection(required("PDV_DB_URL"),
                required("PDV_DB_USER"), required("PDV_DB_PASSWORD")); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + application + "\" CASCADE");
            statement.execute("DROP SCHEMA IF EXISTS \"" + history + "\" CASCADE");
        }
    }

    private String required(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Real PostgreSQL is required: " + key);
        }
        return value;
    }
}
