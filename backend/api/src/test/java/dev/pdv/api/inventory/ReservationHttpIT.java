package dev.pdv.api.inventory;

import dev.pdv.api.support.ReservationHttpSupport;
import java.util.ArrayList;
import java.util.Comparator;
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

class ReservationHttpIT extends ReservationHttpSupport {
    @Test void createRenewAndReadKeepPhysicalAndUseDatabaseDeadline() throws Exception {
        var adminToken = token("ADMIN"); var operatorToken = token("OPERATOR"); var product = stocked(adminToken, category(adminToken), 10);
        UUID request = UUID.randomUUID(); var input = Map.of("requestId", request, "items", List.of(reserveItem(product, 4, 1)));
        var first = send("POST", "/reservations", operatorToken, input); assertThat(first.statusCode()).isEqualTo(201);
        var body = json.readTree(first.body()); UUID id = UUID.fromString(body.get("id").asString());
        assertThat(body.get("status").asString()).isEqualTo("ACTIVE"); assertThat(physical(product)).isEqualTo(10); assertThat(reserved(product)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM expires_at-created_at) FROM inventory_reservations WHERE id=?", Integer.class, id)).isEqualTo(900);
        var replay = send("POST", "/reservations", operatorToken, input); assertThat(replay.body()).isEqualTo(first.body()); assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
        var renew = Map.of("requestId", UUID.randomUUID(), "version", 0); var renewed = send("POST", "/reservations/" + id + "/renew", operatorToken, renew);
        assertThat(renewed.statusCode()).isEqualTo(200); assertThat(json.readTree(renewed.body()).get("version").asLong()).isEqualTo(1);
        assertThat(send("POST", "/reservations/" + id + "/renew", operatorToken, renew).body()).isEqualTo(renewed.body());
        assertThat(send("GET", "/reservations/" + id, operatorToken, null).statusCode()).isEqualTo(200);
        assertThat(json.readTree(send("GET", "/reservations", operatorToken, null).body()).get("total").asLong()).isEqualTo(1);
        assertThat(reserved(product)).isEqualTo(4); assertThat(jdbc.queryForObject("SELECT version FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(2);
        var balance = send("GET", "/inventory/" + product, operatorToken, null); assertThat(balance.headers().firstValue("ETag")).contains("\"inventory-" + product + "-v2\"");
        assertThat(json.readTree(balance.body()).get("available").asLong()).isEqualTo(6);
    }
    @Test void batchRollbackIncludesEarlierBalanceAndKeyMayBeRetriedAfterFailure() throws Exception {
        var token = token("ADMIN"); var category = category(token); var ids = new ArrayList<>(List.of(stocked(token, category, 5), stocked(token, category, 5))); ids.sort(Comparator.comparing(UUID::toString));
        UUID request = UUID.randomUUID(); var input = Map.of("requestId", request, "items", List.of(reserveItem(ids.get(0), 2, 1), reserveItem(ids.get(1), 6, 1)));
        assertThat(send("POST", "/reservations", token, input).statusCode()).isEqualTo(409); assertThat(reserved(ids.get(0))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE actor_id=?", Integer.class, admin)).isZero();
        assertThat(send("POST", "/inventory/" + ids.get(1) + "/movements", token, command(UUID.randomUUID(), "ENTRY", 1, 1)).statusCode()).isEqualTo(201);
        var corrected = Map.of("requestId", request, "items", List.of(reserveItem(ids.get(0), 2, 1), reserveItem(ids.get(1), 6, 2)));
        assertThat(send("POST", "/reservations", token, corrected).statusCode()).isEqualTo(201); assertThat(reserved(ids.get(0))).isEqualTo(2); assertThat(reserved(ids.get(1))).isEqualTo(6);
    }
    @Test void concurrentWritersCannotOversellAndSameKeyHasOneEffect() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 5); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> write = () -> { start.await(5, TimeUnit.SECONDS); return send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(reserveItem(product, 5, 1)))).statusCode(); };
            var one = pool.submit(write); var two = pool.submit(write); start.countDown(); assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(reserved(product)).isEqualTo(5); assertThat(physical(product)).isEqualTo(5);
        assertThat(send("POST", "/inventory/" + product + "/movements", token, command(UUID.randomUUID(), "EXIT", 1, 2)).statusCode()).isEqualTo(409);
    }
    @Test void concurrentDuplicateAndAlteredKeyDoNotReserveTwice() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); var input = Map.of("requestId", UUID.randomUUID(), "items", List.of(reserveItem(product, 2, 1))); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> write = () -> { start.await(5, TimeUnit.SECONDS); var r = send("POST", "/reservations", token, input); assertThat(r.statusCode()).isEqualTo(201); return r.body(); };
            var one = pool.submit(write); var two = pool.submit(write); start.countDown(); assertThat(one.get(30, TimeUnit.SECONDS)).isEqualTo(two.get(30, TimeUnit.SECONDS));
        }
        assertThat(reserved(product)).isEqualTo(2);
        assertThat(send("POST", "/reservations", token, Map.of("requestId", input.get("requestId"), "items", List.of(reserveItem(product, 3, 1)))).statusCode()).isEqualTo(409);
    }
    @Test void ownOnlyAuthorizationAndRevocationApplyToExistingToken() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); UUID id = reserve(token, product, 2); var other = token("OPERATOR");
        assertThat(send("GET", "/reservations/" + id, other, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/reservations/" + id + "/renew", other, Map.of("requestId", UUID.randomUUID(), "version", 0)).statusCode()).isEqualTo(404);
        assertThat(json.readTree(send("GET", "/reservations", other, null).body()).get("total").asLong()).isZero();
        assertThat(send("GET", "/reservations", null, null).statusCode()).isEqualTo(401);
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='reservation')");
        assertThat(send("GET", "/reservations/" + id, token, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/reservations/" + id + "/renew", token, Map.of("requestId", UUID.randomUUID(), "version", 0)).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.1", "1.0"})
    void invalidQuantitiesNeverReserve(String quantity) throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10);
        var item = Map.of("productId", product, "quantity", new java.math.BigDecimal(quantity), "version", 1);
        assertThat(send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(item))).statusCode()).isEqualTo(400); assertThat(reserved(product)).isZero();
    }
    @Test void inactiveProductsDuplicatesAndStaleVersionsAreRejected() throws Exception {
        var token = token("ADMIN"); var category = category(token); var inactive = product(token, category);
        assertThat(send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(reserveItem(inactive, 1, 0)))).statusCode()).isEqualTo(409);
        var product = stocked(token, category, 10); var item = reserveItem(product, 1, 1);
        assertThat(send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(item, item))).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(reserveItem(product, 1, 0)))).statusCode()).isEqualTo(409);
        UUID id = reserve(token, product, 1);
        assertThat(send("PATCH", "/products/" + product + "/status", token, Map.of("status", "INATIVO", "version", 2)).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/reservations/" + id + "/renew", token, Map.of("requestId", UUID.randomUUID(), "version", 0)).statusCode()).isEqualTo(409); assertThat(reserved(product)).isEqualTo(1);
    }
    @Test void expiryReleasesExactlyOnceAndNeverRevivesExpiredReservation() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); UUID id = reserve(token, product, 3); dueSoon(id);
        assertThat(expire(id)).isTrue(); assertThat(expire(id)).isFalse(); assertThat(protect(id)).isFalse();
        assertThat(reserved(product)).isZero(); assertThat(physical(product)).isEqualTo(10);
        assertThat(send("POST", "/reservations/" + id + "/renew", token, Map.of("requestId", UUID.randomUUID(), "version", 2)).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation_events WHERE reservation_id=? AND kind='EXPIRED'", Integer.class, id)).isEqualTo(1);
    }
    @Test void paymentBarrierPreventsExpirationAndCannotBeClearedOrRenewed() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); UUID id = reserve(token, product, 3);
        jdbc.update("UPDATE inventory_reservations SET expires_at=clock_timestamp()+interval '400 milliseconds',version=version+1 WHERE id=?", id);
        assertThat(protect(id)).isTrue(); assertThat(protect(id)).isTrue(); Thread.sleep(450);
        assertThat(expire(id)).isFalse(); assertThat(reserved(product)).isEqualTo(3);
        assertThat(send("POST", "/reservations/" + id + "/renew", token, Map.of("requestId", UUID.randomUUID(), "version", 2)).statusCode()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_reservations SET payment_protected=false,version=version+1 WHERE id=?", id)).isInstanceOf(DataAccessException.class);
    }
    @Test void immutableAuditAndFailedAuditRollBackAllStockEffects() throws Exception {
        var token = token("ADMIN"); var product = stocked(token, category(token), 10); UUID id = reserve(token, product, 2);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_reservation_events WHERE reservation_id=?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_reservation_items SET quantity=3 WHERE reservation_id=?", id)).isInstanceOf(DataAccessException.class);
        String constraint = "fixture_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE inventory_reservation_events ADD CONSTRAINT " + constraint + " CHECK (kind<>'CREATED') NOT VALID");
        try {
            assertThat(send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(reserveItem(product, 1, 2)))).statusCode()).isEqualTo(503);
            assertThat(reserved(product)).isEqualTo(2); assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE actor_id=?", Integer.class, admin)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE inventory_reservation_events DROP CONSTRAINT " + constraint); }
    }
}
