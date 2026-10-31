package dev.pdv.api.access;

import java.util.UUID;
import dev.pdv.api.access.application.TokenCodec;
import dev.pdv.api.access.infrastructure.DevelopmentUserBootstrap;
import dev.pdv.api.support.PostgresFixture;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPersistenceIT {
    private PostgresFixture postgres;
    private JdbcTemplate jdbc;
    private final String hash = new BCryptPasswordEncoder(4).encode("synthetic-test-only");

    @BeforeEach
    void initialize() {
        postgres = new PostgresFixture();
        postgres.flyway().migrate();
        jdbc = postgres.jdbc();
    }

    @AfterEach
    void close() throws Exception {
        postgres.close();
    }

    @Test
    void tokenConstraintsAndCascadeAreEnforcedByPostgres() {
        UUID user = user();
        String digest = TokenCodec.digest(TokenCodec.generate());
        insert(digest, user, 60);
        assertThatThrownBy(() -> insert(digest, user, 60)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(TokenCodec.digest("expired"), user, -1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("invalid", user, 60)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(TokenCodec.digest("unknown"), UUID.randomUUID(), 60))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM users WHERE id = ?", user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens", Integer.class)).isZero();
    }

    @Test
    void tokenIssuanceAndLastLoginRollBackTogether() {
        UUID user = user();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            insert(TokenCodec.digest("rollback"), user, 60);
            jdbc.update("UPDATE users SET last_login_at = clock_timestamp() WHERE id = ?", user);
            throw new IllegalStateException("Synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NULL FROM users WHERE id = ?", Boolean.class, user)).isTrue();
    }

    @Test
    void upgradeFromV2PreservesIdentity() throws Exception {
        try (var older = new PostgresFixture()) {
            older.configured("2").migrate();
            UUID id = older.jdbc().queryForObject("""
                    INSERT INTO users(name,email,password_hash,active) VALUES ('Synthetic','upgrade@example.test',?,true)
                    RETURNING id
                    """, UUID.class, hash);
            assertThat(older.flyway().migrate().migrationsExecuted).isEqualTo(1);
            assertThat(older.jdbc().queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, id)).isTrue();
            assertThat(older.jdbc().queryForObject("SELECT count(*) FROM auth_tokens", Integer.class)).isZero();
        }
    }

    @Test
    void bootstrapRequiresBothDevelopmentProfileAndExplicitOptIn() {
        for (boolean development : new boolean[]{false, true}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                if (development) {
                    context.getEnvironment().setActiveProfiles("development");
                } else {
                    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                            java.util.Map.of("PDV_DEV_BOOTSTRAP_USER", "true")));
                }
                context.register(DevelopmentUserBootstrap.class);
                context.refresh();
                assertThat(context.getBeansOfType(DevelopmentUserBootstrap.class)).isEmpty();
            }
        }
    }

    @Test
    void bootstrapCreatesNewAdminAndRefusesToOverwriteExistingIdentity() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var bootstrap = new DevelopmentUserBootstrap(jdbc, new BCryptPasswordEncoder(4), factory.getValidator(),
                    "bootstrap@example.test", "synthetic-private-input");
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
            transaction.executeWithoutResult(status -> bootstrap.run(null));
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.code='ADMIN'
                    """, Integer.class)).isEqualTo(1);
            String before = jdbc.queryForObject("SELECT password_hash FROM users WHERE email='bootstrap@example.test'", String.class);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> bootstrap.run(null)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT password_hash = ? FROM users WHERE email='bootstrap@example.test'",
                    Boolean.class, before)).isTrue();
        }
    }

    @Test
    void bootstrapDoesNotElevateAnExistingNonAdministrator() {
        UUID existing = user();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var bootstrap = new DevelopmentUserBootstrap(jdbc, new BCryptPasswordEncoder(4), factory.getValidator(),
                    "user@example.test", "synthetic-private-input");
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> bootstrap.run(null)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=?", Integer.class, existing)).isZero();
            assertThat(jdbc.queryForObject("SELECT password_hash = ? FROM users WHERE id=?", Boolean.class, hash, existing)).isTrue();
        }
    }

    private UUID user() {
        return jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic','user@example.test',?) RETURNING id",
                UUID.class, hash);
    }

    private void insert(String digest, UUID user, int seconds) {
        jdbc.update("""
                INSERT INTO auth_tokens(token_hash,user_id,credential_fingerprint,created_at,expires_at)
                VALUES (?,?,?,transaction_timestamp(),transaction_timestamp() + (? * interval '1 second'))
                """, digest, user, TokenCodec.digest(hash), seconds);
    }
}
