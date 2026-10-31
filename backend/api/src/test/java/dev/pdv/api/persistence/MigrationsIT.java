package dev.pdv.api.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** All destructive operations are restricted to this test's UUID namespaces. */
@Timeout(60)
class MigrationsIT {
    private static final String MAIN = "classpath:db/migration";
    private final String application = "pdv_it_" + UUID.randomUUID().toString().replace("-", "");
    private final String history = application + "_history";
    private Flyway flyway;
    @TempDir Path temporary;

    @BeforeEach
    void migrateOwnNamespaces() {
        flyway = configured(MAIN);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
    }

    @AfterEach
    void cleanupOwnNamespaces() throws Exception {
        dropOwnNamespaces();
    }

    @Test
    void replayDoesNotReapplyMigration() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
    }

    @Test
    void rebuildThenReplayRestoresVersionAndSchema() throws Exception {
        var checksum = flyway.info().current().getChecksum();
        dropOwnNamespaces();
        var rebuilt = configured(MAIN);
        assertThat(rebuilt.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(rebuilt.info().current().getChecksum()).isEqualTo(checksum);
        try (var connection = connection();
                var statement = connection.prepareStatement("SELECT count(*) FROM pg_namespace WHERE nspname = ?")) {
            statement.setString(1, application);
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void changedAppliedScriptFailsChecksumValidation() throws Exception {
        try (var source = getClass().getResourceAsStream("/db/migration/V2__identity_roles_permissions.sql")) {
            Files.write(temporary.resolve("V2__identity_roles_permissions.sql"), source.readAllBytes());
        }
        try (var source = getClass().getResourceAsStream("/db/migration/V1__create_application_schema.sql")) {
            assertThat(source).isNotNull();
            var changed = new String(source.readAllBytes(), StandardCharsets.UTF_8) + "\nSELECT 42;\n";
            Files.writeString(temporary.resolve("V1__create_application_schema.sql"), changed);
        }
        var changed = configured("filesystem:" + temporary);
        assertThatThrownBy(changed::validate).isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void failedMigrationRollsBackDdlAndKeepsHistoryValid() throws Exception {
        var failing = configured(MAIN, "classpath:db/test/failure");
        assertThatThrownBy(failing::migrate).isInstanceOf(FlywayException.class);
        try (var connection = connection();
                var statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, application + ".migration_probe");
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getObject(1)).isNull();
            }
        }
        flyway.validate();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
    }

    @Test
    void cleanCannotEraseSchemas() {
        assertThatThrownBy(flyway::clean).isInstanceOf(FlywayException.class).hasMessageContaining("clean");
        flyway.validate();
    }

    @Test
    void concurrentMigratorsApplyIncrementExactlyOnce() throws Exception {
        var first = configured(MAIN, "classpath:db/test/valid");
        var second = configured(MAIN, "classpath:db/test/valid");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test start barrier timed out");
                }
                return first.migrate().migrationsExecuted;
            });
            var right = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test start barrier timed out");
                }
                return second.migrate().migrationsExecuted;
            });
            start.countDown();
            assertThat(left.get(30, TimeUnit.SECONDS) + right.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            try (var connection = connection(); var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM " + identifier(application) + ".migration_probe")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            assertThat(first.info().current().getVersion().toString()).isEqualTo("9000");
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Migration test threads did not stop");
            }
        }
    }

    private Flyway configured(String... locations) {
        return Flyway.configure().dataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD"))
                .defaultSchema(history).schemas(history).locations(locations)
                .placeholders(Map.of("applicationSchema", application))
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .validateMigrationNaming(true).validateOnMigrate(true).load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD"));
    }

    private void dropOwnNamespaces() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + identifier(application) + " CASCADE");
            statement.execute("DROP SCHEMA IF EXISTS " + identifier(history) + " CASCADE");
        }
    }

    private String identifier(String value) {
        if (!value.equals(application) && !value.equals(history)) {
            throw new IllegalArgumentException("Cleanup outside this test is prohibited");
        }
        if (!value.matches("pdv_it_[a-f0-9]{32}(_history)?")) {
            throw new IllegalArgumentException("Invalid test namespace");
        }
        return "\"" + value + "\"";
    }

    private String required(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Real PostgreSQL test environment is required: " + key);
        }
        return value;
    }
}
