package dev.pdv.worker;

import dev.pdv.worker.business.BusinessEnvelope;
import dev.pdv.worker.business.BusinessInbox;
import dev.pdv.worker.business.BusinessOutbox;
import dev.pdv.worker.business.BusinessTopology;
import dev.pdv.worker.business.ConfirmedBusinessPublisher;
import dev.pdv.worker.probe.infrastructure.ProbeTopology;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class BusinessMessagingIT {
    private static final String SCHEMA = "pdv_bus_"+UUID.randomUUID().toString().replace("-", "");
    private static final String HISTORY = SCHEMA+"_history";
    private static final String PREFIX = "pdv.test.business."+UUID.randomUUID();
    private static final String PROBE = "pdv.test.probe."+UUID.randomUUID();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        migrate(); registry.add("pdv.business.enabled", () -> "true"); registry.add("pdv.business.prefix", () -> PREFIX);
        registry.add("pdv.database.schema", () -> SCHEMA); registry.add("pdv.business.interval-ms", () -> "3600000");
        registry.add("pdv.business.consumers-auto-startup", () -> "false"); registry.add("pdv.simulated-payments.enabled", () -> "false"); registry.add("pdv.probe.prefix", () -> PROBE);
    }
    private static Flyway flyway() { return Flyway.configure().dataSource(required("PDV_DB_URL"), required("PDV_DB_USER"), required("PDV_DB_PASSWORD"))
        .defaultSchema(HISTORY).schemas(HISTORY).locations("classpath:db/migration").placeholders(Map.of("applicationSchema", SCHEMA)).cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).load(); }
    private static void migrate() { flyway().migrate(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired BusinessTopology names;
    @Autowired BusinessOutbox outbox;
    @Autowired BusinessInbox inbox;
    @Autowired ConfirmedBusinessPublisher sender;
    @Autowired JsonMapper json;
    private UUID actor;
    private UUID customer;
    private UUID category;
    @BeforeEach void ownFreshState() {
        stopConsumers(); purge();
        if (!SCHEMA.matches("pdv_bus_[a-f0-9]{32}")) { throw new IllegalStateException("Unowned schema"); }
        jdbc.execute("DROP SCHEMA \""+SCHEMA+"\" CASCADE"); jdbc.execute("DROP SCHEMA \""+HISTORY+"\" CASCADE"); migrate();
        actor = jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic','business@example.test',?) RETURNING id", UUID.class, "$2a$04$"+"A".repeat(53));
        customer = jdbc.queryForObject("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic') RETURNING id", UUID.class);
        category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
    }
    @AfterEach void quietConsumers() { stopConsumers(); purge(); }
    private void stopConsumers() { listeners.getListenerContainer("business-requests").stop(); listeners.getListenerContainer("business-results").stop(); }
    private void startRequests() { listeners.getListenerContainer("business-requests").start(); }
    private void startResults() { listeners.getListenerContainer("business-results").start(); }
    private void purge() { admin.purgeQueue(names.requests()); admin.purgeQueue(names.results()); admin.purgeQueue(names.dead()); }
    @AfterAll void cleanupOwnedObjects() {
        stopConsumers(); listeners.stop(); admin.deleteQueue(names.requests()); admin.deleteQueue(names.results()); admin.deleteQueue(names.dead()); admin.deleteExchange(names.exchange()); admin.deleteExchange(names.errors());
        var probe = new ProbeTopology(PROBE); admin.deleteQueue(probe.requests()); admin.deleteQueue(probe.responses()); admin.deleteQueue(probe.dead()); admin.deleteExchange(probe.exchange()); admin.deleteExchange(probe.errorsExchange());
        if (!SCHEMA.matches("pdv_bus_[a-f0-9]{32}") || !HISTORY.equals(SCHEMA+"_history")) { throw new IllegalStateException("Unowned schema"); }
        jdbc.execute("DROP SCHEMA \""+SCHEMA+"\" CASCADE"); jdbc.execute("DROP SCHEMA \""+HISTORY+"\" CASCADE");
    }
    private Operation operation(String scenario) {
        UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock,status) VALUES (?,?,'Synthetic',12.34,0,'UN',0,'ATIVO') RETURNING id", UUID.class, category, UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
        jdbc.update("UPDATE inventory_balances SET physical=10,reserved=2,version=1 WHERE product_id=?", product);
        UUID cart = UUID.randomUUID(); UUID reservation = UUID.randomUUID(); UUID sale = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        jdbc.update("INSERT INTO carts(id,actor_id,customer_id,created_at,updated_at) VALUES (?,?,?,clock_timestamp(),clock_timestamp())", cart, actor, customer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity,unit_price) VALUES (?,?,?,2,12.34)", UUID.randomUUID(), cart, product);
        jdbc.update("INSERT INTO inventory_reservations(id,actor_id,expires_at) VALUES (?,?,clock_timestamp()+interval '900 seconds')", reservation, actor);
        jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,2)", reservation, product); jdbc.query("SELECT record_inventory_reservation_event(?,'CREATED')", (r, i) -> 0, reservation);
        jdbc.update("INSERT INTO sales(id,cart_id,actor_id,customer_id,reservation_id,total) VALUES (?,?,?,?,?,24.68)", sale, cart, actor, customer, reservation);
        jdbc.update("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price) VALUES (?,?,2,12.34)", sale, product);
        jdbc.update("INSERT INTO payments(id,sale_id,method,scenario,amount) VALUES (?,?,'PIX',?,24.68)", payment, sale, scenario);
        jdbc.update("INSERT INTO payment_intents(payment_id,payload) VALUES (?,'{}')", payment); jdbc.update("INSERT INTO sale_events(sale_id,version,kind,snapshot) VALUES (?,0,'CHECKOUT','{}')", sale);
        UUID event = jdbc.queryForObject("SELECT id FROM outbox_events WHERE payment_id=?", UUID.class, payment); return new Operation(sale, payment, reservation, product, event);
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table, Long.class); }
    private TransactionTemplate transaction() { return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())); }
    private BusinessOutbox publisher(BusinessOutbox.Sender send) { return new BusinessOutbox(jdbc, transaction(), send, json, 10); }
    private BusinessEnvelope request(Operation o) { return new BusinessEnvelope(BusinessEnvelope.REQUEST, o.event(), o.payment(), null); }
    private void awaitCount(String table, long expected) { await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(table)).isEqualTo(expected)); }
    private void awaitResults() { await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='pdv.payment.result.v1'", Long.class)).isPositive()); }
    @Test void committedIntentsTraverseRealBrokerAndBothLogicalInboxes() {
        operation("APROVADO"); operation("RECUSADO"); operation("TIMEOUT"); startRequests(); startResults(); assertThat(outbox.runRound().published()).isEqualTo(3); awaitCount("simulation_decisions", 3);
        assertThat(outbox.runRound().published()).isEqualTo(3); awaitCount("payment_results", 3); awaitCount("inbox_messages", 6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_deliveries WHERE status='PUBLISHED'", Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT sum(physical) FROM inventory_balances", Long.class)).isEqualTo(30); assertThat(jdbc.queryForObject("SELECT sum(reserved) FROM inventory_balances", Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE payment_protected", Long.class)).isEqualTo(3); assertThat(count("sale_events")).isEqualTo(9);
    }
    @Test void sameMessageAndDifferentEventForSameOperationDoNotDuplicateBusinessEffect() {
        var o = operation("APROVADO"); startRequests(); startResults(); sender.send(request(o)); sender.send(request(o));
        sender.send(new BusinessEnvelope(BusinessEnvelope.REQUEST, UUID.randomUUID(), o.payment(), null)); awaitCount("simulation_decisions", 1); awaitResults(); outbox.runRound(); awaitCount("payment_results", 1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("inbox_messages")).isGreaterThanOrEqualTo(3)); assertThat(count("sale_events")).isEqualTo(3);
    }
    @Test void failedResponseIntentRollsBackInboxDecisionAndProtectionThenDlqReplaySucceeds() {
        var o = operation("APROVADO"); jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT fixture_fail_response CHECK(event_type<>'pdv.payment.result.v1') NOT VALID"); startRequests(); sender.send(request(o));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.dead()).getMessageCount()).isEqualTo(1));
        assertThat(count("inbox_messages")).isZero(); assertThat(count("simulation_decisions")).isZero(); assertThat(jdbc.queryForObject("SELECT payment_protected FROM inventory_reservations WHERE id=?", Boolean.class, o.reservation())).isFalse();
        jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT fixture_fail_response"); var dead = rabbit.receive(names.dead(), 5000); assertThat(dead).isNotNull();
        rabbit.send(names.exchange(), "payment.request", dead); awaitCount("simulation_decisions", 1); awaitCount("inbox_messages", 1);
    }
    @Test void consumerTransientFailuresAreRetriedThreeTimesThenDeadLettered() {
        var o = operation("APROVADO"); jdbc.execute("CREATE SEQUENCE fixture_fail_count");
        jdbc.execute("CREATE FUNCTION fixture_transient_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM nextval('fixture_fail_count'); RAISE EXCEPTION 'Transient fixture' USING ERRCODE='40001'; END; $$");
        jdbc.execute("CREATE TRIGGER fixture_transient BEFORE INSERT ON simulation_decisions FOR EACH ROW EXECUTE FUNCTION fixture_transient_failure()"); startRequests(); sender.send(request(o));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.dead()).getMessageCount()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT last_value FROM fixture_fail_count", Long.class)).isEqualTo(3); assertThat(count("inbox_messages")).isZero(); assertThat(count("simulation_decisions")).isZero();
        jdbc.execute("DROP TRIGGER fixture_transient ON simulation_decisions"); var message = rabbit.receive(names.dead(), 5000); assertThat(message).isNotNull(); rabbit.send(names.exchange(), "payment.request", message); awaitCount("simulation_decisions", 1);
    }
    @Test void inboxRejectsSameIdentityWithDifferentContentAndLogsNeverContainPayload(CapturedOutput output) {
        var first = operation("APROVADO"); var second = operation("APROVADO"); startRequests(); sender.send(request(first)); awaitCount("inbox_messages", 1);
        sender.send(new BusinessEnvelope(BusinessEnvelope.REQUEST, first.event(), second.payment(), null));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.dead()).getMessageCount()).isEqualTo(1));
        assertThat(count("simulation_decisions")).isEqualTo(1); assertThat(count("inbox_messages")).isEqualTo(1);
        String marker = "DO_NOT_LOG_"+UUID.randomUUID(); var properties = new MessageProperties(); properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(names.exchange(), "payment.request", new Message(("{\"secret\":\""+marker+"\"}").getBytes(StandardCharsets.UTF_8), properties));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.dead()).getMessageCount()).isEqualTo(2)); assertThat(output.getAll().contains(marker)).isFalse();
    }
    @Test void missingRoutingKeepsOutboxRecoverableThenDeadAndRearmPreservesEventIdentity() {
        var o = operation("APROVADO"); admin.removeBinding(BindingBuilder.bind(new Queue(names.requests())).to(new DirectExchange(names.exchange())).with("payment.request"));
        try {
            for (int i=0; i<5; i++) { jdbc.update("UPDATE outbox_deliveries SET next_attempt_at=clock_timestamp() WHERE event_id=?", o.event()); assertThat(outbox.runRound().failed()).isEqualTo(1); }
            assertThat(jdbc.queryForObject("SELECT status FROM outbox_deliveries WHERE event_id=?", String.class, o.event())).isEqualTo("DEAD"); assertThat(count("inbox_messages")).isZero();
        } finally { admin.declareBinding(BindingBuilder.bind(new Queue(names.requests())).to(new DirectExchange(names.exchange())).with("payment.request")); }
        assertThat(jdbc.queryForObject("SELECT retry_outbox_event(?)", Boolean.class, o.event())).isTrue(); assertThat(outbox.runRound().published()).isEqualTo(1);
        var message = rabbit.receive(names.requests(), 5000); assertThat(message).isNotNull(); assertThat(message.getMessageProperties().getMessageId()).isEqualTo(o.event().toString());
    }
    @Test void brokerConnectionFailurePersistsPendingWithoutPretendingToPublish() {
        var o = operation("APROVADO"); var disconnected = new CachingConnectionFactory("127.0.0.1", 1); disconnected.setConnectionTimeout(500);
        try {
            var template = new RabbitTemplate(disconnected); template.setMandatory(true); var unavailable = new ConfirmedBusinessPublisher(template, names, json, 500);
            assertThat(publisher(unavailable).runRound().failed()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM outbox_deliveries WHERE event_id=?", String.class, o.event())).isEqualTo("PENDING"); assertThat(count("inbox_messages")).isZero();
        } finally { disconnected.destroy(); }
    }
    @Test void confirmBeforeDatabaseMarkCanBeRepeatedAndInboxAppliesOnce() {
        var o = operation("APROVADO"); var once = new AtomicBoolean();
        var crashing = publisher(envelope -> { sender.send(envelope); if (once.compareAndSet(false, true)) { throw new DataAccessResourceFailureException("Crash window fixture"); } });
        assertThat(crashing.runRound().failed()).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT status FROM outbox_deliveries WHERE event_id=?", String.class, o.event())).isEqualTo("PENDING");
        assertThat(outbox.runRound().published()).isEqualTo(1); startRequests(); awaitCount("inbox_messages", 1); awaitCount("simulation_decisions", 1); assertThat(count("outbox_events")).isEqualTo(2);
    }
    @Test void commitBeforeAckIsRedeliveredAndRecognizedUsingOriginalIdentity() {
        var o = operation("APROVADO"); sender.send(request(o));
        var connection = ((CachingConnectionFactory)rabbit.getConnectionFactory()).createConnection(); var channel = connection.createChannel(false);
        try {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.requests()).getMessageCount()).isEqualTo(1));
            var delivery = channel.basicGet(names.requests(), false); assertThat(delivery).isNotNull(); inbox.process(BusinessInbox.SIMULATOR, json.readValue(delivery.getBody(), BusinessEnvelope.class));
            channel.abort(); startRequests();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.requests()).getMessageCount()).isZero());
            assertThat(count("inbox_messages")).isEqualTo(1); assertThat(count("simulation_decisions")).isEqualTo(1);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Broker fixture failed", failure); }
        finally { connection.close(); }
    }
    @Test void twoPublishersAndTwoInboxWritersArbitrateWithoutDuplicatingEffect() throws Exception {
        var o = operation("APROVADO"); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return outbox.runRound().published(); }); var b=pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return publisher(sender).runRound().published(); }); gate.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)+b.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return inbox.process(BusinessInbox.SIMULATOR, request(o)).replayed(); }); var b=pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return inbox.process(BusinessInbox.SIMULATOR, request(o)).replayed(); }); start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)^b.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(count("simulation_decisions")).isEqualTo(1); assertThat(count("inbox_messages")).isEqualTo(1);
    }
    @Test void applicationFailureRollsBackCoordinatorInboxAndPaymentResult() {
        var o = operation("APROVADO"); inbox.process(BusinessInbox.SIMULATOR, request(o));
        var result = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE event_type='pdv.payment.result.v1'", String.class); var envelope = json.readValue(result, BusinessEnvelope.class);
        jdbc.execute("ALTER TABLE sale_events ADD CONSTRAINT fixture_no_approve CHECK(kind<>'APROVADO') NOT VALID");
        assertThatThrownBy(() -> inbox.process(BusinessInbox.COORDINATOR, envelope)).isInstanceOf(DataAccessException.class); assertThat(count("payment_results")).isZero(); assertThat(count("inbox_messages")).isEqualTo(1);
        jdbc.execute("ALTER TABLE sale_events DROP CONSTRAINT fixture_no_approve"); assertThat(inbox.process(BusinessInbox.COORDINATOR, envelope).replayed()).isFalse(); assertThat(count("payment_results")).isEqualTo(1);
    }
    @Test void envelopeValidationRejectsUnsafeContractsButIgnoresProducerClassHeaders() {
        var o=operation("APROVADO"); startRequests();
        var valid=new MessageProperties(); valid.setContentType(MessageProperties.CONTENT_TYPE_JSON); valid.setMessageId(o.event().toString()); valid.setHeader("__TypeId__", "java.lang.Runtime");
        rabbit.send(names.exchange(), "payment.request", new Message(json.writeValueAsBytes(request(o)), valid)); awaitCount("inbox_messages",1);
        for (String scenario : java.util.List.of("oversized","mime","identity","unknownField","wrongType")) {
            var properties=new MessageProperties(); properties.setContentType(MessageProperties.CONTENT_TYPE_JSON); properties.setMessageId(o.event().toString()); byte[] body=json.writeValueAsBytes(request(o));
            switch(scenario) {
                case "oversized" -> body=new byte[4097];
                case "mime" -> properties.setContentType("application/x-java-serialized-object");
                case "identity" -> properties.setMessageId(UUID.randomUUID().toString());
                case "unknownField" -> body=("{\"type\":\"pdv.payment.requested.v1\",\"eventId\":\""+o.event()+"\",\"paymentId\":\""+o.payment()+"\",\"outcome\":null,\"unsafe\":true}").getBytes(StandardCharsets.UTF_8);
                case "wrongType" -> body=json.writeValueAsBytes(new BusinessEnvelope(BusinessEnvelope.RESULT,o.event(),o.payment(),"APROVADO"));
                default -> throw new IllegalStateException("Unknown fixture");
            }
            rabbit.send(names.exchange(),"payment.request",new Message(body,properties));
        }
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo(names.dead()).getMessageCount()).isEqualTo(5)); assertThat(count("inbox_messages")).isEqualTo(1);
    }
    private static String required(String name) { var v=System.getenv(name); if(v==null || v.isBlank()) { throw new IllegalStateException("Real infrastructure required"); } return v; }
    private record Operation(UUID sale, UUID payment, UUID reservation, UUID product, UUID event) { }
}
