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
        for (var action : List.of("checkout", "read", "reconcile")) {
            UUID id = UUID.randomUUID(); grants.add(id);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'sale',?)", id, action);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", id);
        }
    }
    @BeforeEach void simulationPermissions() {
        UUID id=UUID.randomUUID(); grants.add(id);
        jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'simulation','resolve')",id);
        // Both grants deliberately present: the role guard must still deny OPERATOR.
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')",id);
    }
    @AfterEach void cleanupSales() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String db = connection.getCatalog();
            if (!(db.matches("pdv_verify_[a-f0-9]{32}") || (db.equals("pdv_ci") && "true".equals(System.getenv("CI"))))) { throw new IllegalStateException("Unowned fixture database"); }
            if (!connection.getAutoCommit()) { throw new IllegalStateException("Fixture transaction must be owned"); }
            try {
                connection.setAutoCommit(false);
                try (var s = connection.createStatement()) { s.execute("SET LOCAL session_replication_role=replica"); }
                for (var sql : List.of("DELETE FROM outbox_deliveries WHERE event_id IN (SELECT e.id FROM outbox_events e JOIN payments p ON p.id=e.payment_id JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM outbox_events WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM inbox_messages WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM simulation_decisions WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM payment_command_operations WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM provider_resolutions WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM reservation_release_items WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM reservation_releases WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM sale_sagas WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM sale_stock_movements WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM sale_confirmations WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM receipts WHERE sale_id IN (SELECT id FROM sales WHERE actor_id IN (?,?))",
                        "DELETE FROM payment_results WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
                        "DELETE FROM payment_intents WHERE payment_id IN (SELECT p.id FROM payments p JOIN sales s ON s.id=p.sale_id WHERE s.actor_id IN (?,?))",
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
    private tools.jackson.databind.JsonNode uncertain(String owner, String administrator) throws Exception {
        UUID product=stocked(administrator,category(administrator),10); UUID cart=filled(owner,product,2);
        var response=checkout(cart,owner,UUID.randomUUID().toString(),Map.of("version",1,"method","PIX","scenario","TIMEOUT")); assertThat(response.statusCode()).isEqualTo(201);
        var sale=json.readTree(response.body()); UUID payment=UUID.fromString(sale.get("paymentId").asString());
        jdbc.queryForObject("SELECT prepare_business_payment(?)",String.class,payment); jdbc.queryForObject("SELECT apply_business_payment_result(?,'INDETERMINADO')",String.class,payment); return sale;
    }
    private String resolutionPath(tools.jackson.databind.JsonNode sale) { return "/simulator/payments/"+sale.get("paymentId").asString()+"/resolution"; }
    private String reconcilePath(tools.jackson.databind.JsonNode sale) { return "/sales/"+sale.get("id").asString()+"/reconcile"; }
    private Map<String,Object> resolveInput(UUID request, String outcome) { return Map.of("requestId",request,"outcome",outcome,"reason","Synthetic decision"); }
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
            for (var table : List.of("sales", "payments", "payment_intents", "sale_items", "checkout_operations", "outbox_events", "outbox_deliveries")) { assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero(); }
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
        UUID payment=UUID.fromString(json.readTree(response.body()).get("paymentId").asString());
        jdbc.queryForObject("SELECT start_sale_payment(?)",String.class,payment); jdbc.queryForObject("SELECT record_simulated_payment(?,'APROVADO')",String.class,payment);
        assertThat(jdbc.queryForObject("SELECT confirm_business_sale(?)",String.class,payment)).isEqualTo("CONCLUIDA");
        var receipt=send("GET","/sales/"+id+"/receipt",token,null); assertThat(receipt.statusCode()).isEqualTo(200);
        assertThat(json.readValue(receipt.body(),dev.pdv.api.sales.application.Receipt.class).total()).isEqualByComparingTo(price.multiply(java.math.BigDecimal.valueOf(Long.MAX_VALUE)));
        assertThat(jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?",Long.class,product)).isZero();
    }
    @Test void ownReceiptIsStableAfterConclusionWithPrivateAccessAndAutomaticHistory() throws Exception {
        UUID inventoryRead=UUID.randomUUID(); grants.add(inventoryRead);
        jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'inventory','read')",inventoryRead);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')",inventoryRead);
        var operatorToken=token("OPERATOR"); var adminToken=token("ADMIN"); var product=stocked(adminToken,category(adminToken),10); var cart=filled(operatorToken,product,2);
        var sale=json.readTree(checkout(cart,operatorToken,"receipt",command(1)).body()); UUID payment=UUID.fromString(sale.get("paymentId").asString()); String path="/sales/"+sale.get("id").asString()+"/receipt";
        assertThat(send("GET",path,operatorToken,null).statusCode()).isEqualTo(404);
        jdbc.queryForObject("SELECT prepare_business_payment(?)",String.class,payment); jdbc.queryForObject("SELECT apply_business_payment_result(?,'APROVADO')",String.class,payment);
        assertThat(send("GET",path,operatorToken,null).statusCode()).isEqualTo(404); jdbc.queryForObject("SELECT confirm_business_sale(?)",String.class,payment);
        var receipt=send("GET",path,operatorToken,null); assertThat(receipt.statusCode()).isEqualTo(200); assertThat(receipt.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(json.readTree(receipt.body()).get("total").decimalValue()).isEqualByComparingTo("24.68");
        jdbc.update("UPDATE products SET sale_price=99,status='INATIVO' WHERE id=?",product); jdbc.queryForObject("SELECT confirm_business_sale(?)",String.class,payment);
        assertThat(send("GET",path,operatorToken,null).body()).isEqualTo(receipt.body()); assertThat(send("GET",path,adminToken,null).statusCode()).isEqualTo(404); assertThat(send("GET",path,null,null).statusCode()).isEqualTo(401);
        var history=send("GET","/inventory/"+product+"/movements",operatorToken,null); assertThat(history.statusCode()).isEqualTo(200); assertThat(history.body()).contains("SALE", "-2");
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='sale' AND action='read')");
        assertThat(send("GET",path,operatorToken,null).statusCode()).isEqualTo(403);
    }
    @Test void uncertainReconciliationStaysPendingThenAdminCanResolveOtherPaymentWithoutReadingSale() throws Exception {
        var owner=token("OPERATOR"); var administrator=token("ADMIN"); var sale=uncertain(owner,administrator); UUID request=UUID.randomUUID(); String path=reconcilePath(sale);
        var pending=send("POST",path,owner,Map.of("requestId",request)); assertThat(pending.statusCode()).isEqualTo(202); assertThat(pending.body()).contains("INDETERMINADO","ACTIVE");
        assertThat(send("POST",resolutionPath(sale),owner,resolveInput(UUID.randomUUID(),"APROVADO")).statusCode()).isEqualTo(403);
        UUID resolutionRequest=UUID.randomUUID(); var decision=send("POST",resolutionPath(sale),administrator,resolveInput(resolutionRequest,"APROVADO")); assertThat(decision.statusCode()).isEqualTo(200);
        assertThat(json.readTree(decision.body()).propertyNames()).containsExactlyInAnyOrder("paymentId","outcome","recordedAt");
        var replay=send("POST",resolutionPath(sale),administrator,resolveInput(resolutionRequest,"APROVADO")); assertThat(replay.body()).isEqualTo(decision.body()); assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
        assertThat(send("POST",resolutionPath(sale),administrator,resolveInput(resolutionRequest,"RECUSADO")).statusCode()).isEqualTo(409);
        assertThat(send("POST",resolutionPath(sale),administrator,resolveInput(UUID.randomUUID(),"RECUSADO")).statusCode()).isEqualTo(409);
        assertThat(send("POST",path,administrator,Map.of("requestId",UUID.randomUUID())).statusCode()).isEqualTo(404);
        String salePath="/sales/"+sale.get("id").asString(); assertThat(send("GET",salePath,administrator,null).statusCode()).isEqualTo(404);
        var completed=send("POST",path,owner,Map.of("requestId",UUID.randomUUID())); assertThat(completed.statusCode()).isEqualTo(200); assertThat(completed.body()).contains("CONCLUIDA","CONFIRMED");
        assertThat(send("GET",salePath+"/receipt",owner,null).statusCode()).isEqualTo(200); assertThat(send("GET",salePath+"/receipt",administrator,null).statusCode()).isEqualTo(404);
        var old=send("POST",path,owner,Map.of("requestId",request)); assertThat(old.statusCode()).isEqualTo(202); assertThat(old.body()).isEqualTo(pending.body());
        assertThat(send("POST",path,null,Map.of("requestId",UUID.randomUUID())).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT outcome FROM payment_results WHERE payment_id=?",String.class,UUID.fromString(sale.get("paymentId").asString()))).isEqualTo("INDETERMINADO");
    }
    @Test void lateRefusalReleasesOnlyReservedAndReconcilesIdempotently() throws Exception {
        var owner=token("OPERATOR"); var administrator=token("ADMIN"); var sale=uncertain(owner,administrator); UUID payment=UUID.fromString(sale.get("paymentId").asString());
        assertThat(send("POST",resolutionPath(sale),administrator,resolveInput(UUID.randomUUID(),"RECUSADO")).statusCode()).isEqualTo(200);
        UUID request=UUID.randomUUID(); var first=send("POST",reconcilePath(sale),owner,Map.of("requestId",request)); assertThat(first.statusCode()).isEqualTo(200); assertThat(first.body()).contains("RELEASED","PAGAMENTO_RECUSADO");
        assertThat(send("POST",reconcilePath(sale),owner,Map.of("requestId",request)).body()).isEqualTo(first.body());
        assertThat(send("POST",reconcilePath(sale),owner,Map.of("requestId",UUID.randomUUID())).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_releases WHERE payment_id=?",Long.class,payment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?",Long.class,UUID.fromString(sale.get("items").get(0).get("productId").asString()))).isEqualTo(10);
        assertThat(send("GET","/sales/"+sale.get("id").asString()+"/receipt",owner,null).statusCode()).isEqualTo(404);
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='sale' AND action='reconcile')");
        assertThat(send("POST",reconcilePath(sale),owner,Map.of("requestId",request)).statusCode()).isEqualTo(403);
    }
    @Test void resolutionAndReconciliationValidateContractsAndCurrentPermissions() throws Exception {
        var administrator=token("ADMIN"); var sale=uncertain(administrator,administrator); String path=resolutionPath(sale);
        for(var body:List.of(Map.of("outcome","APROVADO","reason","Synthetic"),Map.of("requestId",UUID.randomUUID(),"outcome","INDETERMINADO","reason","Synthetic"),
                Map.of("requestId",UUID.randomUUID(),"outcome","APROVADO","reason"," "),Map.of("requestId",UUID.randomUUID(),"outcome","APROVADO","reason","x".repeat(501)),
                Map.of("requestId",UUID.randomUUID(),"outcome","APROVADO","reason","Synthetic","saleId",UUID.randomUUID()))) {
            assertThat(send("POST",path,administrator,body).statusCode()).isEqualTo(400);
        }
        assertThat(send("POST",reconcilePath(sale),administrator,Map.of("requestId",UUID.randomUUID(),"outcome","APROVADO")).statusCode()).isEqualTo(400);
        assertThat(send("POST",reconcilePath(sale),administrator,Map.of()).statusCode()).isEqualTo(400);
        assertThat(send("POST",path,null,resolveInput(UUID.randomUUID(),"APROVADO")).statusCode()).isEqualTo(401);
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='simulation')");
        assertThat(send("POST",path,administrator,resolveInput(UUID.randomUUID(),"APROVADO")).statusCode()).isEqualTo(403);
    }
    @Test void commandAuditFailureRollsBackResolutionAndAtomicReconciliation() throws Exception {
        var administrator=token("ADMIN"); var sale=uncertain(administrator,administrator); UUID payment=UUID.fromString(sale.get("paymentId").asString()); UUID request=UUID.randomUUID();
        jdbc.execute("ALTER TABLE payment_command_operations ADD CONSTRAINT fixture_command_fail CHECK(FALSE) NOT VALID");
        try { assertThat(send("POST",resolutionPath(sale),administrator,resolveInput(request,"APROVADO")).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_resolutions WHERE payment_id=?",Long.class,payment)).isZero();
        } finally { jdbc.execute("ALTER TABLE payment_command_operations DROP CONSTRAINT fixture_command_fail"); }
        assertThat(send("POST",resolutionPath(sale),administrator,resolveInput(request,"APROVADO")).statusCode()).isEqualTo(200);
        UUID reconcileRequest=UUID.randomUUID(); jdbc.execute("ALTER TABLE payment_command_operations ADD CONSTRAINT fixture_command_fail CHECK(FALSE) NOT VALID");
        try { assertThat(send("POST",reconcilePath(sale),administrator,Map.of("requestId",reconcileRequest)).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?",String.class,payment)).isEqualTo("INDETERMINADO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM receipts WHERE sale_id=?",Long.class,UUID.fromString(sale.get("id").asString()))).isZero();
        } finally { jdbc.execute("ALTER TABLE payment_command_operations DROP CONSTRAINT fixture_command_fail"); }
        assertThat(send("POST",reconcilePath(sale),administrator,Map.of("requestId",reconcileRequest)).statusCode()).isEqualTo(200);
    }
    @Test void concurrentOppositeResolutionsChooseOneProofAndDuplicateReconcileCannotRepeatEffect() throws Exception {
        var administrator=token("ADMIN"); var sale=uncertain(administrator,administrator); var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return send("POST",resolutionPath(sale),administrator,resolveInput(UUID.randomUUID(),"APROVADO")).statusCode();});
            var b=pool.submit(()->{gate.await(5,TimeUnit.SECONDS);return send("POST",resolutionPath(sale),administrator,resolveInput(UUID.randomUUID(),"RECUSADO")).statusCode();});gate.countDown();
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        var start=new CountDownLatch(1); UUID request=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> call=()->{start.await(5,TimeUnit.SECONDS);return send("POST",reconcilePath(sale),administrator,Map.of("requestId",request));};
            var a=pool.submit(call); var b=pool.submit(call);start.countDown();var one=a.get(30,TimeUnit.SECONDS);var two=b.get(30,TimeUnit.SECONDS);
            assertThat(one.statusCode()).isEqualTo(200);assertThat(two.statusCode()).isEqualTo(200);assertThat(one.body()).isEqualTo(two.body());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_resolutions WHERE payment_id=?",Long.class,UUID.fromString(sale.get("paymentId").asString()))).isEqualTo(1);
    }
}
