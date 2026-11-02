package dev.pdv.api.sales;

import dev.pdv.api.support.CartHttpSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutHttpIT extends CartHttpSupport {
    private final List<UUID> grants = new ArrayList<>();
    @BeforeEach void checkoutPermissions() {
        for (var action : List.of("checkout", "read")) {
            UUID id = UUID.randomUUID(); grants.add(id);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'sale',?)", id, action);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", id);
        }
    }
    @AfterEach void cleanupSales() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String db = connection.getCatalog();
            if (!(db.matches("pdv_verify_[a-f0-9]{32}") || (db.equals("pdv_ci") && "true".equals(System.getenv("CI"))))) { throw new IllegalStateException("Unowned fixture database"); }
            if (!connection.getAutoCommit()) { throw new IllegalStateException("Fixture transaction must be owned"); }
            try {
                connection.setAutoCommit(false);
                try (var s = connection.createStatement()) { s.execute("SET LOCAL session_replication_role=replica"); }
                for (var sql : List.of("DELETE FROM payment_intents WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM payments WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))", "DELETE FROM sale_events WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))", "DELETE FROM checkout_operations WHERE actor_id IN (?,?)", "DELETE FROM sales WHERE actor_id IN (?,?)")) {
                    try (var s = connection.prepareStatement(sql)) { s.setObject(1, admin); s.setObject(2, operator); s.executeUpdate(); }
                }
                connection.commit();
            } catch (java.sql.SQLException | RuntimeException e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
            return null;
        });
        for (var id : grants) { jdbc.update("DELETE FROM permissions WHERE id=?", id); }
    }
    private Map<String, Object> command(long version) { return Map.of("version", version, "method", "PIX", "scenario", "APROVADO"); }
    private UUID stocked(String token, UUID category, long quantity) throws Exception {
        var id = activeProduct(token, category); jdbc.update("UPDATE inventory_balances SET physical=? WHERE product_id=?", quantity, id); return id;
    }
    private UUID filled(String token, UUID product, long quantity) throws Exception {
        UUID cart = cart(token); assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", quantity, "version", 0)).statusCode()).isEqualTo(200); return cart;
    }
    private HttpResponse<String> checkout(UUID cart, String token, String key, Object input) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/carts/" + cart + "/checkout")).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        if (key != null) { b.header("Idempotency-Key", key); }
        return HttpClient.newHttpClient().send(b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void createsOneAtomicSnapshotAndFreezesCartWithoutDebitingPhysicalOrStartingPayment() throws Exception {
        var token = token("OPERATOR"); var adminToken = token("ADMIN"); var product = stocked(adminToken, category(adminToken), 10); var cart = filled(token, product, 3);
        var response = checkout(cart, token, UUID.randomUUID().toString(), command(1)); assertThat(response.statusCode()).isEqualTo(201); var sale = json.readTree(response.body());
        assertThat(sale.get("total").decimalValue()).isEqualByComparingTo("37.02"); assertThat(sale.get("status").asString()).isEqualTo("AGUARDANDO_PAGAMENTO"); assertThat(sale.get("paymentStatus").asString()).isEqualTo("CRIADO");
        assertThat(jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_intents WHERE payment_id=?", Integer.class, UUID.fromString(sale.get("paymentId").asString()))).isEqualTo(1);
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 4, "version", 2)).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", itemPath(cart, product) + "?version=2", token, null).statusCode()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("UPDATE cart_items SET quantity=4 WHERE cart_id=?", cart)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM sales WHERE cart_id=?", cart)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM sale_events WHERE sale_id=?", UUID.fromString(sale.get("id").asString()))).isInstanceOf(DataAccessException.class);
        assertThat(send("GET", "/sales/" + sale.get("id").asString(), token, null).statusCode()).isEqualTo(200);
    }
    @Test void knownReplaySurvivesPriceAndActiveChangesAndConflictingKeyOrSecondKeyCannotDuplicate() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); var cart = filled(token, product, 2); String key = UUID.randomUUID().toString();
        var created = checkout(cart, token, key, command(1)); assertThat(created.statusCode()).isEqualTo(201);
        jdbc.update("UPDATE products SET sale_price=99,status='INATIVO' WHERE id=?", product); jdbc.update("UPDATE customers SET active=false WHERE id=?", customer());
        var replay = checkout(cart, token, key, command(1)); assertThat(replay.statusCode()).isEqualTo(201); assertThat(replay.body()).isEqualTo(created.body()); assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
        assertThat(checkout(cart, token, key, command(2)).statusCode()).isEqualTo(409); assertThat(checkout(cart, token, UUID.randomUUID().toString(), command(2)).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales WHERE cart_id=?", Integer.class, cart)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(2);
    }
    @Test void ownershipAndPermissionAreRecheckedEvenForReplay() throws Exception {
        var token = token("OPERATOR"); var other = token("ADMIN"); var product = stocked(other, category(other), 10); var cart = filled(token, product, 1); String key = UUID.randomUUID().toString();
        assertThat(checkout(cart, other, key, command(1)).statusCode()).isEqualTo(404); var response = checkout(cart, token, key, command(1)); assertThat(response.statusCode()).isEqualTo(201);
        String id = json.readTree(response.body()).get("id").asString(); assertThat(send("GET", "/sales/" + id, other, null).statusCode()).isEqualTo(404);
        assertThat(checkout(cart, null, key, command(1)).statusCode()).isEqualTo(401); assertThat(send("GET", "/sales/" + id, null, null).statusCode()).isEqualTo(401);
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='sale')");
        assertThat(checkout(cart, token, key, command(1)).statusCode()).isEqualTo(403); assertThat(send("GET", "/sales/" + id, token, null).statusCode()).isEqualTo(403);
    }
    @Test void validatesKeyVersionContractAndEmptyCart() throws Exception {
        var token = token("ADMIN"); var cart = cart(token);
        for (var key : java.util.Arrays.asList(null, "", "a b", "x".repeat(129), "a,b")) { assertThat(checkout(cart, token, key, command(0)).statusCode()).isEqualTo(400); }
        assertThat(checkout(cart, token, "empty", command(0)).statusCode()).isEqualTo(409);
        for (var body : List.of(Map.of("version", -1, "method", "PIX", "scenario", "APROVADO"), Map.of("version", 0, "method", "REAL", "scenario", "APROVADO"),
                Map.of("version", 0, "method", "PIX", "scenario", "RANDOM"), Map.of("version", 0, "method", "PIX", "scenario", "APROVADO", "total", 0))) {
            assertThat(checkout(cart, token, "invalid", body).statusCode()).isEqualTo(400);
        }
    }
    @Test void changedPriceInactiveCustomerAndProductAndStaleVersionFailBeforeReservation() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 5); var cart = filled(token, product, 1);
        assertThat(checkout(cart, token, "stale", command(0)).statusCode()).isEqualTo(409);
        jdbc.update("UPDATE products SET sale_price=13 WHERE id=?", product); var changed = checkout(cart, token, "price", command(1)); assertThat(changed.statusCode()).isEqualTo(409); assertThat(changed.body()).contains("price_changed");
        jdbc.update("UPDATE products SET sale_price=12.34,status='INATIVO' WHERE id=?", product); assertThat(checkout(cart, token, "inactive", command(1)).statusCode()).isEqualTo(409);
        jdbc.update("UPDATE products SET status='ATIVO' WHERE id=?", product); jdbc.update("UPDATE customers SET active=false WHERE id=?", customer()); assertThat(checkout(cart, token, "customer", command(1)).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales WHERE cart_id=?", Integer.class, cart)).isZero();
    }
    @Test void missingSecondBalanceRollsBackFirstReservationAndCartRemainsEditable() throws Exception {
        var token = token("ADMIN"); var category = category(token); var first = stocked(token, category, 10); var second = stocked(token, category, 0); var cart = filled(token, first, 1);
        assertThat(send("PUT", itemPath(cart, second), token, Map.of("quantity", 1, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(checkout(cart, token, "batch", command(2)).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT sum(reserved) FROM inventory_balances WHERE product_id IN (?,?)", Long.class, first, second)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cart)).isEqualTo(2);
        assertThat(send("DELETE", itemPath(cart, second) + "?version=2", token, null).statusCode()).isEqualTo(200);
    }
    @Test void auditFailureRollsBackEveryEffectAndSameKeyCanThenSucceed() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 5); var cart = filled(token, product, 1); String constraint = "fixture_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE sale_events ADD CONSTRAINT " + constraint + " CHECK (kind<>'CHECKOUT') NOT VALID");
        try {
            assertThat(checkout(cart, token, "retry", command(1)).statusCode()).isEqualTo(503);
            for (var table : List.of("sales", "payments", "payment_intents", "sale_items", "checkout_operations")) { assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero(); }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE actor_id=?", Integer.class, admin)).isZero();
            assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isZero();
            assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cart)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE sale_events DROP CONSTRAINT " + constraint); }
        assertThat(checkout(cart, token, "retry", command(1)).statusCode()).isEqualTo(201);
    }
    @Test void concurrentSameKeyReturnsSameResponseAndDifferentKeysCannotCreateTwoSales() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); var cart = filled(token, product, 1); String key = UUID.randomUUID().toString(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> task = () -> { start.await(5, TimeUnit.SECONDS); return checkout(cart, token, key, command(1)); };
            var one = pool.submit(task); var two = pool.submit(task); start.countDown(); var a = one.get(30, TimeUnit.SECONDS); var b = two.get(30, TimeUnit.SECONDS);
            assertThat(a.statusCode()).isEqualTo(201); assertThat(b.statusCode()).isEqualTo(201); assertThat(a.body()).isEqualTo(b.body());
        }
        var another = filled(token, product, 1); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return checkout(another, token, "one", command(1)).statusCode(); });
            var b = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return checkout(another, token, "two", command(1)).statusCode(); }); gate.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(2);
    }
    @Test void twoCartsCompetingForLastUnitCannotOversell() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 1); var first = filled(token, product, 1); var second = filled(token, product, 1); var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return checkout(first, token, "first", command(1)).statusCode(); });
            var b = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return checkout(second, token, "second", command(1)).statusCode(); }); gate.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(1);
    }
    @Test void extremeAllowedMoneyIsStoredWithoutRoundingOrOverflow() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), Long.MAX_VALUE); var price = new java.math.BigDecimal("99999999999999999.99"); jdbc.update("UPDATE products SET sale_price=? WHERE id=?", price, product);
        var cart = filled(token, product, Long.MAX_VALUE); var response = checkout(cart, token, "extreme", command(1)); assertThat(response.statusCode()).isEqualTo(201);
        var id = UUID.fromString(json.readTree(response.body()).get("id").asString());
        assertThat(jdbc.queryForObject("SELECT total FROM sales WHERE id=?", java.math.BigDecimal.class, id)).isEqualByComparingTo(price.multiply(java.math.BigDecimal.valueOf(Long.MAX_VALUE)));
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE sale_id=?", java.math.BigDecimal.class, id)).isEqualByComparingTo(price.multiply(java.math.BigDecimal.valueOf(Long.MAX_VALUE)));
    }
}
