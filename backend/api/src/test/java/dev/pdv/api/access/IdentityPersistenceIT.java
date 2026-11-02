package dev.pdv.api.access;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import dev.pdv.api.access.infrastructure.DevelopmentIdentitySeed;
import dev.pdv.api.access.infrastructure.IdentityRepository;
import dev.pdv.api.support.PostgresFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(60)
class IdentityPersistenceIT {
    private static final String HASH = new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString());
    private final PostgresFixture database = new PostgresFixture();
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrate() {
        database.flyway().migrate();
        jdbc = database.jdbc();
    }

    @AfterEach
    void cleanupOwnNamespaces() throws Exception {
        database.close();
    }

    @Test
    void emailIsUniqueAndCanonicalAtDatabaseBoundary() {
        user("unique@example.test");
        assertThatThrownBy(() -> user("unique@example.test")).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> user(" Mixed@Example.test ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPlainPasswordAndBlankName() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users(name,email,password_hash) VALUES (?,?,?)",
                "Synthetic", "hash@example.test", "not-a-password-hash")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users(name,email,password_hash) VALUES (?,?,?)",
                " ", "blank@example.test", HASH)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void associationsEnforceForeignKeysAndUniquePairs() {
        var user = user("links@example.test");
        var role = role("ADMIN");
        var permission = permission();
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", user, role);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", role, permission);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", user, role))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", role, permission))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", UUID.randomUUID(), role))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", role, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleAndPermissionCatalogsAreUnique() {
        role("ADMIN");
        permission();
        assertThatThrownBy(() -> role("ADMIN")).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(this::permission).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void deletingRoleRemovesLinksAndPreservesUser() {
        var user = user("preserved@example.test");
        var role = role("ADMIN");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", user, role);
        jdbc.update("DELETE FROM roles WHERE id = ?", role);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class)).isZero();
        assertThat(new IdentityRepository(jdbc).findById(user)).isPresent();
    }

    @Test
    void repositoryReadsTypedIdentityAndDeduplicatesAuthorities() {
        var user = user("reader@example.test");
        var first = role("ADMIN");
        var second = role("AUDITOR");
        var permission = permission();
        for (var role : new UUID[] {first, second}) {
            jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", user, role);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", role, permission);
        }
        var repository = new IdentityRepository(jdbc);
        var identity = repository.findByEmail(" READER@EXAMPLE.TEST ").orElseThrow();
        assertThat(identity.id()).isEqualTo(user);
        assertThat(identity.passwordHash()).isEqualTo(HASH);
        assertThat(identity.toString()).doesNotContain(HASH, identity.email());
        assertThat(repository.roles(user)).containsExactly("ADMIN", "AUDITOR");
        assertThat(repository.authorities(user)).containsExactly("access.permissions.read");
        assertThat(repository.permissions()).hasSize(1);
    }

    @Test
    void failedTransactionDoesNotLeavePartialUser() {
        user("existing@example.test");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            user("rollback@example.test");
            user("existing@example.test");
        })).isInstanceOf(DuplicateKeyException.class);
        assertThat(new IdentityRepository(jdbc).findByEmail("rollback@example.test")).isEmpty();
    }

    @Test
    void concurrentDuplicateEmailsHaveOnlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> insertConcurrent(start));
            var second = executor.submit(() -> insertConcurrent(start));
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email='race@example.test'", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Identity test threads did not stop");
            }
        }
    }

    @Test
    void developmentSeedIsOptInIdempotentAndHasNoAccounts() throws Exception {
        try (var normal = new AnnotationConfigApplicationContext()) {
            normal.registerBean(JdbcTemplate.class, () -> jdbc);
            normal.register(DevelopmentIdentitySeed.class);
            normal.refresh();
            assertThat(normal.getBeansOfType(DevelopmentIdentitySeed.class)).isEmpty();
        }
        try (var development = new AnnotationConfigApplicationContext()) {
            development.getEnvironment().setActiveProfiles("development");
            development.registerBean(JdbcTemplate.class, () -> jdbc);
            development.register(DevelopmentIdentitySeed.class);
            development.refresh();
            var seed = development.getBean(DevelopmentIdentitySeed.class);
            seed.run(null);
            seed.run(null);
        }
        assertThat(jdbc.queryForList("SELECT code FROM roles ORDER BY code", String.class)).containsExactly("ADMIN", "OPERATOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForList("""
                SELECT r.code FROM roles r JOIN role_permissions rp ON rp.role_id=r.id
                JOIN permissions p ON p.id=rp.permission_id WHERE p.resource='access.permissions' AND p.action='read'
                """, String.class)).containsExactly("ADMIN");
    }

    @Test
    void upgradesFromV1PreservingDataAndRebuildsIdentitySchema() throws Exception {
        try (var older = new PostgresFixture()) {
            older.configured("1").migrate();
            var olderJdbc = older.jdbc();
            olderJdbc.execute("CREATE TABLE preserved_marker (id INTEGER PRIMARY KEY)");
            olderJdbc.update("INSERT INTO preserved_marker VALUES (42)");
            assertThat(older.flyway().migrate().migrationsExecuted).isEqualTo(dev.pdv.api.support.MigrationBaseline.COUNT - 1);
            assertThat(olderJdbc.queryForObject("SELECT id FROM preserved_marker", Integer.class)).isEqualTo(42);
            older.close();
            assertThat(older.flyway().migrate().migrationsExecuted).isEqualTo(dev.pdv.api.support.MigrationBaseline.COUNT);
            assertThat(older.jdbc().queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        }
    }

    private int insertConcurrent(CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Identity test barrier timed out");
        }
        try {
            user("race@example.test");
            return 1;
        } catch (DuplicateKeyException expected) {
            return 0;
        }
    }

    private UUID user(String email) {
        return jdbc.queryForObject("INSERT INTO users(name,email,password_hash,active) VALUES ('Synthetic',?,?,true) RETURNING id",
                UUID.class, email, HASH);
    }

    private UUID role(String code) {
        return jdbc.queryForObject("INSERT INTO roles(code,name) VALUES (?,?) RETURNING id", UUID.class, code, code);
    }

    private UUID permission() {
        return jdbc.queryForObject("INSERT INTO permissions(resource,action) VALUES ('access.permissions','read') RETURNING id", UUID.class);
    }
}
