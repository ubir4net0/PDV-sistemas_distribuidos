package dev.pdv.api.inventory;

import dev.pdv.api.support.MovementHttpSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryMovementHttpIT extends MovementHttpSupport {
    @Test
    void entryExitAndAbsoluteAdjustmentAreAuditedAndReplayReturnsOriginalResult() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product + "/movements";
        var firstInput = command(UUID.randomUUID(), "ENTRY", 10, 0); var first = send("POST", path, token, firstInput);
        assertThat(first.statusCode()).isEqualTo(201); assertThat(first.headers().firstValue("Idempotent-Replay")).contains("false");
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "EXIT", 2, 1)).statusCode()).isEqualTo(201);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "ADJUST", 5, 2)).statusCode()).isEqualTo(201);
        assertThat(physical(product)).isEqualTo(5);
        var replay = send("POST", path, token, firstInput); assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true"); assertThat(replay.body()).isEqualTo(first.body());
        var history = json.readTree(send("GET", path, token("OPERATOR"), null).body()).get("items"); assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(0).get("actorId").asString()).isEqualTo(admin.toString());
        assertThat(history.get(0).get("delta").asLong()).isEqualTo(10); assertThat(history.get(1).get("delta").asLong()).isEqualTo(-2);
        assertThat(history.get(2).get("delta").asLong()).isEqualTo(-3); assertThat(history.get(2).get("balance").get("version").asLong()).isEqualTo(3);
    }
    @Test
    void batchIsCanonicalAndReorderingItemsDoesNotApplyTwice() throws Exception {
        var token = token("ADMIN"); var category = category(token); var first = product(token, category); var second = product(token, category); var request = UUID.randomUUID();
        var items = List.of(item(first, "ENTRY", 4, 0), item(second, "ENTRY", 7, 0));
        var response = send("POST", "/inventory/movements/batches", token, Map.of("requestId", request, "items", items)); assertThat(response.statusCode()).isEqualTo(201);
        var replay = send("POST", "/inventory/movements/batches", token, Map.of("requestId", request, "items", List.of(items.get(1), items.get(0))));
        assertThat(replay.body()).isEqualTo(response.body()); assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
        assertThat(physical(first)).isEqualTo(4); assertThat(physical(second)).isEqualTo(7);
    }
    @Test
    void failureOnSecondCanonicalItemRollsBackWholeBatchAndDoesNotConsumeRequestId() throws Exception {
        var token = token("ADMIN"); var category = category(token); var ids = new ArrayList<>(List.of(product(token, category), product(token, category)));
        ids.sort(java.util.Comparator.comparing(UUID::toString)); var request = UUID.randomUUID();
        var bad = List.of(item(ids.get(0), "ENTRY", 8, 0), item(ids.get(1), "EXIT", 1, 0));
        assertThat(send("POST", "/inventory/movements/batches", token, Map.of("requestId", request, "items", bad)).statusCode()).isEqualTo(409);
        assertThat(physical(ids.get(0))).isZero(); assertThat(physical(ids.get(1))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_operations WHERE actor_id=?", Integer.class, admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM inventory_balances WHERE product_id=?", Long.class, ids.get(0))).isZero();
        var good = List.of(item(ids.get(0), "ENTRY", 8, 0), item(ids.get(1), "ENTRY", 1, 0));
        assertThat(send("POST", "/inventory/movements/batches", token, Map.of("requestId", request, "items", good)).statusCode()).isEqualTo(201);
    }
    @Test
    void ledgerFailureRollsBackBalanceVersionAndOperationHeader() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var input = command(UUID.randomUUID(), "ENTRY", 5, 0);
        String constraint = "reject_movement_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE inventory_movements ADD CONSTRAINT " + constraint + " CHECK(product_id <> '" + product + "'::uuid) NOT VALID");
        try {
            assertThat(send("POST", "/inventory/" + product + "/movements", token, input).statusCode()).isEqualTo(503);
            assertThat(physical(product)).isZero(); assertThat(jdbc.queryForObject("SELECT version FROM inventory_balances WHERE product_id=?", Long.class, product)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_operations WHERE actor_id=?", Integer.class, admin)).isZero();
        } finally { jdbc.execute("ALTER TABLE inventory_movements DROP CONSTRAINT " + constraint); }
        assertThat(send("POST", "/inventory/" + product + "/movements", token, input).statusCode()).isEqualTo(201);
    }
    @Test
    void reservedUnitsCannotBeRemovedAndInactiveProductsCanBeCorrected() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product + "/movements";
        jdbc.update("UPDATE products SET status='INATIVO' WHERE id=?", product);
        jdbc.update("UPDATE inventory_balances SET physical=10,reserved=7 WHERE product_id=?", product);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "EXIT", 4, 0)).statusCode()).isEqualTo(409);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "ADJUST", 6, 0)).statusCode()).isEqualTo(409);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "EXIT", 3, 0)).statusCode()).isEqualTo(201);
        assertThat(physical(product)).isEqualTo(7);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "ADJUST", 7, 1)).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(7);
    }
    @Test
    void twoWritersWithSameVersionHaveOneEffect() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> change = () -> { start.await(5, TimeUnit.SECONDS); return send("POST", "/inventory/" + product + "/movements", token, command(UUID.randomUUID(), "ENTRY", 1, 0)).statusCode(); };
            var first = executor.submit(change); var second = executor.submit(change); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(physical(product)).isEqualTo(1);
    }
    @Test
    void concurrentSameRequestIsReplayedAndDifferentContentConflicts() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var request = UUID.randomUUID(); var input = command(request, "ENTRY", 1, 0); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<java.net.http.HttpResponse<String>> change = () -> { start.await(5, TimeUnit.SECONDS); return send("POST", "/inventory/" + product + "/movements", token, input); };
            var first = executor.submit(change); var second = executor.submit(change); start.countDown(); var one = first.get(30, TimeUnit.SECONDS); var two = second.get(30, TimeUnit.SECONDS);
            assertThat(one.statusCode()).isEqualTo(201); assertThat(two.statusCode()).isEqualTo(201); assertThat(one.body()).isEqualTo(two.body());
            assertThat(List.of(one.headers().firstValue("Idempotent-Replay").orElseThrow(), two.headers().firstValue("Idempotent-Replay").orElseThrow())).containsExactlyInAnyOrder("true", "false");
        }
        assertThat(physical(product)).isEqualTo(1);
        assertThat(send("POST", "/inventory/" + product + "/movements", token, command(request, "ENTRY", 2, 0)).statusCode()).isEqualTo(409);
    }
    @Test
    void movementAndOperationHistoryAreImmutableAndAccessIsRevocable() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product + "/movements";
        var input = command(UUID.randomUUID(), "ENTRY", 1, 0);
        assertThat(send("POST", path, null, input).statusCode()).isEqualTo(401);
        assertThat(send("POST", path, token("OPERATOR"), input).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/inventory/movements/batches", token("OPERATOR"), Map.of("requestId", UUID.randomUUID(), "items", List.of(item(product, "ENTRY", 1, 0)))).statusCode()).isEqualTo(403);
        assertThat(send("POST", path, token, input).statusCode()).isEqualTo(201);
        for (String sql : List.of("UPDATE inventory_movements SET reason=reason WHERE product_id=?", "DELETE FROM inventory_movements WHERE product_id=?")) {
            assertThatThrownBy(() -> jdbc.update(sql, product)).isInstanceOf(DataAccessException.class);
        }
        for (String sql : List.of("UPDATE inventory_operations SET response=response WHERE actor_id=?", "DELETE FROM inventory_operations WHERE actor_id=?")) {
            assertThatThrownBy(() -> jdbc.update(sql, admin)).isInstanceOf(DataAccessException.class);
        }
        assertThat(send("GET", path, token("OPERATOR"), null).statusCode()).isEqualTo(200);
        revokeWrite(); assertThat(send("POST", path, token, input).statusCode()).isEqualTo(403);
        revokeRead(); assertThat(send("GET", path, token, null).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest
    @ValueSource(strings = {"quantity:-1", "quantity:0", "quantity:1.1", "quantity:1.0", "reason: ", "version:-1", "type:UNKNOWN", "actorId:forged"})
    void rejectsInvalidOrFractionalInputWithoutEffect(String value) throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var input = new HashMap<>(command(UUID.randomUUID(), "ENTRY", 1, 0)); var field = value.split(":", 2);
        input.put(field[0], "quantity".equals(field[0]) && field[1].contains(".") ? Double.valueOf(field[1]) : field[1]);
        assertThat(send("POST", "/inventory/" + product + "/movements", token, input).statusCode()).isEqualTo(400); assertThat(physical(product)).isZero();
    }
    @Test
    void bigintAndVersionLimitsAreConflictsNotOverflowingWrites() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product + "/movements";
        jdbc.update("UPDATE inventory_balances SET physical=? WHERE product_id=?", Long.MAX_VALUE, product);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "ENTRY", 1, 0)).statusCode()).isEqualTo(409); assertThat(physical(product)).isEqualTo(Long.MAX_VALUE);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "EXIT", Long.MAX_VALUE, 0)).statusCode()).isEqualTo(201); assertThat(physical(product)).isZero();
        jdbc.update("UPDATE inventory_balances SET version=? WHERE product_id=?", Long.MAX_VALUE, product);
        assertThat(send("POST", path, token, command(UUID.randomUUID(), "ENTRY", 1, Long.MAX_VALUE)).statusCode()).isEqualTo(409);
    }
    @Test
    void duplicateBatchProductsAndMissingReferencesAreRejected() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var item = item(product, "ENTRY", 1, 0);
        assertThat(send("POST", "/inventory/movements/batches", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(item, item))).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/inventory/movements/batches", token, Map.of("requestId", UUID.randomUUID(), "items", List.of())).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/inventory/" + UUID.randomUUID() + "/movements", token, command(UUID.randomUUID(), "ENTRY", 1, 0)).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/inventory/" + product + "/movements?size=101", token, null).statusCode()).isEqualTo(400);
    }
}
