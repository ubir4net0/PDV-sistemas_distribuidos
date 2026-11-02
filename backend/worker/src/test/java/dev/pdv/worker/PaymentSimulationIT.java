package dev.pdv.worker;

import dev.pdv.worker.inventory.ReservationExpiryConfiguration;
import dev.pdv.worker.payment.PaymentSimulation;
import dev.pdv.worker.payment.PaymentSimulationConfiguration;
import dev.pdv.worker.probe.infrastructure.ProbeTopology;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PaymentSimulationIT {
    private final String schema = "pdv_pay_" + UUID.randomUUID().toString().replace("-", "");
    private final String history = schema + "_history";
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private UUID actor;
    private UUID customer;
    private UUID category;
    @BeforeEach void migratePrivateSchema() {
        Flyway.configure().dataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD"))
                .defaultSchema(history).schemas(history).locations("classpath:db/migration").placeholders(Map.of("applicationSchema", schema))
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).validateMigrationNaming(true).load().migrate();
        source = new DriverManagerDataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD")); source.setSchema(schema); jdbc = new JdbcTemplate(source);
        actor = jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic','payment@example.test',?) RETURNING id", UUID.class, "$2a$04$" + "A".repeat(53));
        customer = jdbc.queryForObject("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic') RETURNING id", UUID.class);
        category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
    }
    @AfterEach void cleanupPrivateSchema() {
        if (!schema.matches("pdv_pay_[a-f0-9]{32}") || !history.equals(schema + "_history")) { throw new IllegalStateException("Unowned schema"); }
        if (jdbc != null) { jdbc.execute("DROP SCHEMA \"" + schema + "\" CASCADE"); jdbc.execute("DROP SCHEMA \"" + history + "\" CASCADE"); }
    }
    private Operation operation(String scenario, boolean due) {
        UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock,status) VALUES (?,?,'Synthetic',12.34,0,'UN',0,'ATIVO') RETURNING id", UUID.class, category, UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
        jdbc.update("UPDATE inventory_balances SET physical=10,reserved=2,version=1 WHERE product_id=?", product);
        UUID cart = UUID.randomUUID(); UUID reservation = UUID.randomUUID(); UUID sale = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        jdbc.update("INSERT INTO carts(id,actor_id,customer_id,created_at,updated_at) VALUES (?,?,?,clock_timestamp(),clock_timestamp())", cart, actor, customer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity,unit_price) VALUES (?,?,?,2,12.34)", UUID.randomUUID(), cart, product);
        jdbc.update("INSERT INTO inventory_reservations(id,actor_id,created_at,expires_at) VALUES (?,?,clock_timestamp()-interval '2 seconds',clock_timestamp()+(? * interval '1 second'))", reservation, actor, due ? -1 : 900);
        jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,2)", reservation, product);
        jdbc.query("SELECT record_inventory_reservation_event(?,'CREATED')", (r, i) -> 0, reservation);
        jdbc.update("INSERT INTO sales(id,cart_id,actor_id,customer_id,reservation_id,total) VALUES (?,?,?,?,?,24.68)", sale, cart, actor, customer, reservation);
        jdbc.update("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price) VALUES (?,?,2,12.34)", sale, product);
        jdbc.update("INSERT INTO payments(id,sale_id,method,scenario,amount) VALUES (?,?,'PIX',?,24.68)", payment, sale, scenario);
        jdbc.update("INSERT INTO payment_intents(payment_id,payload) SELECT id,jsonb_build_object('version',1,'paymentId',id,'saleId',sale_id,'method',method,'scenario',scenario,'amount',amount) FROM payments WHERE id=?", payment);
        jdbc.update("INSERT INTO sale_events(sale_id,version,kind,snapshot) VALUES (?,0,'CHECKOUT','{}')", sale); return new Operation(sale, payment, reservation, product);
    }
    private PaymentSimulation job(int size) { return new PaymentSimulation(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)), size); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    @ParameterizedTest @CsvSource({"APROVADO,APROVADO,PAGAMENTO_APROVADO", "RECUSADO,RECUSADO,PAGAMENTO_RECUSADO", "TIMEOUT,INDETERMINADO,AGUARDANDO_PAGAMENTO"})
    void deterministicScenariosArePersistedOnceAcrossNewWorkerInstances(String scenario, String outcome, String saleStatus) {
        var o = operation(scenario, false); var first = job(100).runRound(); assertThat(first.completed()).isEqualTo(1); assertThat(first.failed()).isZero(); assertThat(job(100).runRound().scanned()).isZero();
        assertThat(jdbc.queryForObject("SELECT outcome FROM payment_results WHERE payment_id=?", String.class, o.payment())).isEqualTo(outcome);
        assertThat(jdbc.queryForObject("SELECT status FROM sales WHERE id=?", String.class, o.sale())).isEqualTo(saleStatus);
        assertThat(count("payment_results")).isEqualTo(1); assertThat(count("sale_events")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, o.product())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isTrue();
    }
    @Test void twoWorkersCanObserveSameOperationWithoutDuplicatingDecision() throws Exception {
        var o = operation("APROVADO", false); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<PaymentSimulation.Round> task = () -> { gate.await(5, TimeUnit.SECONDS); return job(100).runRound(); };
            var a = pool.submit(task); var b = pool.submit(task); gate.countDown(); assertThat(a.get(30, TimeUnit.SECONDS).failed() + b.get(30, TimeUnit.SECONDS).failed()).isZero();
        }
        job(100).runRound(); assertThat(count("payment_results")).isEqualTo(1); assertThat(count("sale_events")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT version FROM payments WHERE id=?", Long.class, o.payment())).isEqualTo(2);
    }
    @Test void retriesFailedOutcomeAfterCommittedStartWithoutLosingReservation() {
        var o = operation("APROVADO", false); jdbc.execute("ALTER TABLE sale_events ADD CONSTRAINT fixture_fail_result CHECK(kind<>'APROVADO') NOT VALID");
        var failed = job(100).runRound(); assertThat(failed.failed()).isEqualTo(1); assertThat(count("payment_results")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, o.payment())).isEqualTo("EM_PROCESSAMENTO");
        assertThat(jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isTrue();
        jdbc.execute("ALTER TABLE sale_events DROP CONSTRAINT fixture_fail_result"); assertThat(job(100).runRound().completed()).isEqualTo(1); assertThat(count("payment_results")).isEqualTo(1);
    }
    @Test void expiredRequestsDoNotStarveEligibleRequestsAndBatchIsBounded() {
        operation("RECUSADO", true); operation("APROVADO", false); operation("TIMEOUT", false);
        assertThat(job(1).runRound().completed()).isEqualTo(1); assertThat(job(1).runRound().completed()).isEqualTo(1); assertThat(job(1).runRound().scanned()).isZero();
        assertThat(count("payment_results")).isEqualTo(2); assertThatThrownBy(() -> job(101)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bothJobsShareOneDatasourceAndScheduledRecoveryKeepsExpiredPaymentProtected() {
        var o = operation("TIMEOUT", false); jdbc.update("UPDATE inventory_reservations SET expires_at=clock_timestamp()+interval '2 seconds',version=version+1 WHERE id=?", o.reservation());
        assertThat(jdbc.queryForObject("SELECT start_sale_payment(?)", String.class, o.payment())).isEqualTo("EM_PROCESSAMENTO");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("ownSimulation", Map.of("pdv.simulated-payments.enabled", "true", "pdv.reservation-expiry.enabled", "true", "pdv.database.schema", schema)));
            context.register(PaymentSimulationConfiguration.class, ReservationExpiryConfiguration.class); context.refresh(); assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, o.payment())).isEqualTo("INDETERMINADO"));
            assertThat(jdbc.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, o.reservation())).isFalse();
        }
    }
    @Test void disabledJobsDoNotCreateDatasourceOrRequireDatabaseConfiguration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("disabled", Map.of("pdv.simulated-payments.enabled", "false", "pdv.reservation-expiry.enabled", "false")));
            context.register(PaymentSimulationConfiguration.class, ReservationExpiryConfiguration.class); context.refresh(); assertThat(context.getBeansOfType(DataSource.class)).isEmpty(); assertThat(context.getBeansOfType(PaymentSimulation.class)).isEmpty();
        }
    }
    @Test void configurationFailsWhenRequiredMigrationFunctionsAreMissing() {
        jdbc.execute("DROP FUNCTION start_sale_payment(uuid)");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("missingMigration", Map.of("pdv.simulated-payments.enabled", "true", "pdv.database.schema", schema)));
            context.register(PaymentSimulationConfiguration.class); assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalStateException.class);
        }
    }
    @Test void executableJarProcessesAllScenariosAndResumesStartedPayment() throws Exception {
        var approved = operation("APROVADO", false); var refused = operation("RECUSADO", false); var unknown = operation("TIMEOUT", false);
        assertThat(jdbc.queryForObject("SELECT start_sale_payment(?)", String.class, unknown.payment())).isEqualTo("EM_PROCESSAMENTO");
        var names = new ProbeTopology("pdv.test." + UUID.randomUUID()); var log = Files.createTempFile(Path.of("target"), "payment-worker-", ".log");
        var factory = new CachingConnectionFactory(required("PDV_RABBITMQ_HOST"), Integer.parseInt(required("PDV_RABBITMQ_PORT")));
        factory.setUsername(required("PDV_RABBITMQ_USER")); factory.setPassword(required("PDV_RABBITMQ_PASSWORD")); factory.setVirtualHost(required("PDV_RABBITMQ_VHOST")); var admin = new RabbitAdmin(factory);
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", Path.of("target", "pdv-worker-0.0.1-SNAPSHOT.jar").toAbsolutePath().toString(),
                "--pdv.simulated-payments.enabled=true", "--pdv.reservation-expiry.enabled=true", "--pdv.simulated-payments.interval-ms=100", "--pdv.reservation-expiry.interval-ms=100", "--pdv.probe.prefix=" + names.prefix());
        builder.environment().put("PDV_DB_SCHEMA", schema); builder.redirectErrorStream(true).redirectOutput(log.toFile()); Process process = null;
        try {
            process = builder.start();
            await().atMost(Duration.ofSeconds(35)).untilAsserted(() -> assertThat(count("payment_results")).isEqualTo(3));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(admin.getQueueProperties(names.requests())).isNotNull());
            assertThat(process.isAlive()).isTrue(); assertThat(count("sale_events")).isEqualTo(9);
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, approved.payment())).isEqualTo("APROVADO");
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, refused.payment())).isEqualTo("RECUSADO");
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, unknown.payment())).isEqualTo("INDETERMINADO");
            assertThat(jdbc.queryForObject("SELECT sum(physical) FROM inventory_balances", Long.class)).isEqualTo(30); assertThat(jdbc.queryForObject("SELECT sum(reserved) FROM inventory_balances", Long.class)).isEqualTo(6);
            assertThat(Files.readString(log).contains("Tomcat")).isFalse();
        } finally {
            if (process != null) { process.destroy(); if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
            try { admin.deleteQueue(names.requests()); admin.deleteQueue(names.responses()); admin.deleteQueue(names.dead()); admin.deleteExchange(names.exchange()); admin.deleteExchange(names.errorsExchange()); }
            finally { factory.destroy(); Files.deleteIfExists(log); }
        }
    }
    private String required(String name) { var value = System.getenv(name); if (value == null || value.isBlank()) { throw new IllegalStateException("Real infrastructure required"); } return value; }
    private record Operation(UUID sale, UUID payment, UUID reservation, UUID product) { }
}
