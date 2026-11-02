package dev.pdv.worker;

import dev.pdv.worker.inventory.ReservationExpiry;
import dev.pdv.worker.inventory.ReservationExpiryConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationExpiryIT {
    private final String schema = "pdv_worker_" + UUID.randomUUID().toString().replace("-", "");
    private final String history = schema + "_history";
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private UUID actor;
    private UUID category;
    @BeforeAll void migrateOwnedSchema() {
        Flyway.configure().dataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD"))
                .defaultSchema(history).schemas(history).locations("classpath:db/migration").placeholders(Map.of("applicationSchema", schema))
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateMigrationNaming(true).load().migrate();
        source = new DriverManagerDataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD")); source.setSchema(schema); jdbc = new JdbcTemplate(source);
        actor = jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic',?,?) RETURNING id", UUID.class, UUID.randomUUID() + "@example.test", "$2a$04$" + "A".repeat(53));
        category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
    }
    @AfterAll void cleanupOwnedSchema() {
        if (!schema.matches("pdv_worker_[a-f0-9]{32}") || !history.equals(schema + "_history")) { throw new IllegalStateException("Unowned schema"); }
        if (jdbc != null) { jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE"); jdbc.execute("DROP SCHEMA \"" + history + "\" CASCADE"); }
    }
    private ReservationExpiry job(int size) { return new ReservationExpiry(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)), size); }
    private UUID reservation(boolean due, int products) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_reservations(id,actor_id,created_at,expires_at)
                VALUES (?,?,clock_timestamp()-interval '2 seconds',clock_timestamp()+(? * interval '1 second'))
                """, id, actor, due ? -1 : 900);
        for (int i = 0; i < products; i++) {
            UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock,status) VALUES (?,?,'Synthetic',0,0,'UN',0,'ATIVO') RETURNING id", UUID.class, category, UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
            jdbc.update("UPDATE inventory_balances SET physical=10,reserved=3 WHERE product_id=?", product);
            jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,3)", id, product);
        }
        jdbc.query("SELECT record_inventory_reservation_event(?,'CREATED')", (r, i) -> 0, id); return id;
    }
    private long remaining(UUID id) { return jdbc.queryForObject("SELECT sum(b.reserved) FROM inventory_balances b JOIN inventory_reservation_items i ON i.product_id=b.product_id WHERE i.reservation_id=?", Long.class, id); }
    @Test void workerExpiresWholeReservationOnceAndPersistsStatusAudit() {
        UUID id = reservation(true, 2); var first = job(100).runRound(); assertThat(first.expired()).isEqualTo(1); assertThat(first.failed()).isZero();
        assertThat(job(100).runRound().expired()).isZero(); assertThat(remaining(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(b.physical) FROM inventory_balances b JOIN inventory_reservation_items i ON i.product_id=b.product_id WHERE i.reservation_id=?", Long.class, id)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE id=?", String.class, id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation_events WHERE reservation_id=? AND kind='EXPIRED'", Integer.class, id)).isEqualTo(1);
    }
    @Test void twoWorkerInstancesDoNotReleaseTwice() throws Exception {
        UUID id = reservation(true, 1); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<ReservationExpiry.Round> run = () -> { start.await(5, TimeUnit.SECONDS); return job(100).runRound(); };
            var one = pool.submit(run); var two = pool.submit(run); start.countDown(); assertThat(one.get(30, TimeUnit.SECONDS).expired() + two.get(30, TimeUnit.SECONDS).expired()).isEqualTo(1);
        }
        assertThat(remaining(id)).isZero(); assertThat(jdbc.queryForObject("SELECT version FROM inventory_reservations WHERE id=?", Long.class, id)).isEqualTo(1);
    }
    @Test void paymentTransactionHoldsPastDeadlineAndExpirySkipsLockedThenProtectedRow() throws Exception {
        UUID id = reservation(false, 1);
        jdbc.update("UPDATE inventory_reservations SET expires_at=clock_timestamp()+interval '2 seconds',version=version+1 WHERE id=?", id);
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT protect_inventory_reservation(?)")) {
                statement.setObject(1, id); try (var result = statement.executeQuery()) { result.next(); assertThat(result.getBoolean(1)).isTrue(); }
            }
            Thread.sleep(2100); assertThat(job(100).runRound().expired()).isZero(); assertThat(remaining(id)).isEqualTo(3); connection.commit();
        }
        assertThat(job(100).runRound().scanned()).isZero(); assertThat(remaining(id)).isEqualTo(3);
    }
    @Test void expiryWinsBeforeLatePaymentProtectionAndCannotBeRevived() {
        UUID id = reservation(true, 1); assertThat(job(100).runRound().expired()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT protect_inventory_reservation(?)", Boolean.class, id)).isFalse(); assertThat(remaining(id)).isZero();
    }
    @Test void boundedRoundLeavesRemainderForNextPoll() {
        for (int i = 0; i < 3; i++) { reservation(true, 1); }
        assertThat(job(2).runRound().scanned()).isEqualTo(2); assertThat(job(2).runRound().expired()).isEqualTo(1);
        assertThatThrownBy(() -> job(101)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void failedBalanceUpdateRollsBackEarlierItemAndRetriesSafely() {
        UUID id = reservation(true, 2); var ids = jdbc.queryForList("SELECT product_id FROM inventory_reservation_items WHERE reservation_id=? ORDER BY product_id", UUID.class, id);
        jdbc.update("UPDATE inventory_balances SET reserved=0 WHERE product_id=?", ids.get(1));
        assertThat(job(100).runRound().failed()).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, ids.get(0))).isEqualTo(3);
        jdbc.update("UPDATE inventory_balances SET reserved=3 WHERE product_id=?", ids.get(1)); assertThat(job(100).runRound().expired()).isEqualTo(1); assertThat(remaining(id)).isZero();
    }
    @Test void failedAuditRollsBackStatusAndReservedThenRecovers() {
        UUID id = reservation(true, 1); String constraint = "fixture_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE inventory_reservation_events ADD CONSTRAINT " + constraint + " CHECK (kind<>'EXPIRED') NOT VALID");
        try { assertThat(job(100).runRound().failed()).isEqualTo(1); assertThat(remaining(id)).isEqualTo(3); }
        finally { jdbc.execute("ALTER TABLE inventory_reservation_events DROP CONSTRAINT " + constraint); }
        assertThat(job(100).runRound().expired()).isEqualTo(1); assertThat(remaining(id)).isZero();
    }
    @Test void renewedReservationDoesNotExpireUsingAnOldCandidate() {
        UUID id = reservation(false, 1); jdbc.update("UPDATE inventory_reservations SET expires_at=clock_timestamp()+interval '901 seconds',version=version+1 WHERE id=?", id);
        assertThat(jdbc.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, id)).isFalse(); assertThat(remaining(id)).isEqualTo(3);
    }
    @Test void actualScheduledBeanRunsAgainstRealPostgres() {
        UUID id = reservation(true, 1);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ownExpiry", Map.of("pdv.reservation-expiry.enabled", "true",
                    "pdv.reservation-expiry.schema", schema, "pdv.reservation-expiry.interval-ms", "5000")));
            context.register(ReservationExpiryConfiguration.class); context.refresh();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(remaining(id)).isZero());
        }
    }
    private String required(String name) { var value = System.getenv(name); if (value == null || value.isBlank()) { throw new IllegalStateException("Real PostgreSQL required"); } return value; }
}
