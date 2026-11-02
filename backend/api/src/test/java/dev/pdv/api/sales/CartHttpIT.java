package dev.pdv.api.sales;

import dev.pdv.api.support.CartHttpSupport;
import java.math.BigDecimal;
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

class CartHttpIT extends CartHttpSupport {
    @Test void addChangeRemoveCalculateTotalsAndPreserveCapturedPriceWithoutReserving() throws Exception {
        var adminToken = token("ADMIN"); var operatorToken = token("OPERATOR"); var category = category(adminToken); var first = activeProduct(adminToken, category); var second = activeProduct(adminToken, category); var cart = cart(operatorToken);
        assertThat(send("PUT", itemPath(cart, first), operatorToken, Map.of("quantity", 3, "version", 0)).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE products SET sale_price=99.99 WHERE id=?", first);
        var changed = send("PUT", itemPath(cart, first), operatorToken, Map.of("quantity", 4, "version", 1));
        assertThat(changed.statusCode()).isEqualTo(200); assertThat(json.readTree(changed.body()).get("total").decimalValue()).isEqualByComparingTo("49.36");
        var added = send("PUT", itemPath(cart, second), operatorToken, Map.of("quantity", 2, "version", 2)); assertThat(added.statusCode()).isEqualTo(200);
        assertThat(json.readTree(added.body()).get("total").decimalValue()).isEqualByComparingTo("74.04");
        var removed = send("DELETE", itemPath(cart, first) + "?version=3", operatorToken, null); assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(removed.body()).get("total").decimalValue()).isEqualByComparingTo("24.68");
        assertThat(send("PUT", itemPath(cart, first), operatorToken, Map.of("quantity", 1, "version", 4)).statusCode()).isEqualTo(200);
        var read = json.readTree(send("GET", "/carts/" + cart, operatorToken, null).body()); assertThat(read.get("total").decimalValue()).isEqualByComparingTo("124.67");
        assertThat(read.get("version").asLong()).isEqualTo(5); assertThat(jdbc.queryForObject("SELECT sum(reserved+physical) FROM inventory_balances WHERE product_id IN (?,?)", Long.class, first, second)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_changes WHERE cart_id=?", Integer.class, cart)).isEqualTo(6);
    }
    @Test void creationIsIdempotentAndConflictingContentDoesNotCreateAnotherCart() throws Exception {
        var token = token("ADMIN"); var input = Map.of("requestId", UUID.randomUUID(), "customerId", customer());
        var created = send("POST", "/carts", token, input); assertThat(created.statusCode()).isEqualTo(201);
        var replay = send("POST", "/carts", token, input); assertThat(replay.body()).isEqualTo(created.body()); assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
        assertThat(send("POST", "/carts", token, Map.of("requestId", input.get("requestId"), "customerId", UUID.randomUUID())).statusCode()).isEqualTo(409);
        assertThat(json.readTree(send("GET", "/carts", token, null).body()).get("total").asLong()).isEqualTo(1);
    }
    @Test void onlyOwnCartCanBeReadOrChangedAndRevokedPermissionAppliesImmediately() throws Exception {
        var token = token("ADMIN"); var product = activeProduct(token, category(token)); var cart = cart(token); var other = token("OPERATOR");
        assertThat(send("GET", "/carts/" + cart, other, null).statusCode()).isEqualTo(404);
        assertThat(send("PUT", itemPath(cart, product), other, Map.of("quantity", 1, "version", 0)).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", itemPath(cart, product) + "?version=0", other, null).statusCode()).isEqualTo(404);
        assertThat(json.readTree(send("GET", "/carts", other, null).body()).get("total").asLong()).isZero();
        assertThat(send("GET", "/carts", null, null).statusCode()).isEqualTo(401);
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='cart')");
        assertThat(send("GET", "/carts/" + cart, token, null).statusCode()).isEqualTo(403);
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 1, "version", 0)).statusCode()).isEqualTo(403);
    }
    @Test void inactiveCustomerOrProductPreventsNewItemsButObsoleteItemCanBeRemoved() throws Exception {
        var token = token("ADMIN"); UUID customer = customer(); jdbc.update("UPDATE customers SET active=false WHERE id=?", customer);
        assertThat(send("POST", "/carts", token, Map.of("requestId", UUID.randomUUID(), "customerId", customer)).statusCode()).isEqualTo(409);
        jdbc.update("UPDATE customers SET active=true WHERE id=?", customer); var cart = cart(token); var category = category(token); var product = product(token, category);
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 1, "version", 0)).statusCode()).isEqualTo(409);
        var active = activeProduct(token, category); assertThat(send("PUT", itemPath(cart, active), token, Map.of("quantity", 1, "version", 0)).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE products SET status='INATIVO' WHERE id=?", active);
        assertThat(send("PUT", itemPath(cart, active), token, Map.of("quantity", 2, "version", 1)).statusCode()).isEqualTo(409);
        jdbc.update("UPDATE customers SET active=false WHERE id=?", customer);
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 1, "version", 1)).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", itemPath(cart, active) + "?version=1", token, null).statusCode()).isEqualTo(200);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.1", "1.0"})
    void invalidQuantityCannotBeTruncatedOrStored(String quantity) throws Exception {
        var token = token("ADMIN"); var product = activeProduct(token, category(token)); var cart = cart(token);
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", new BigDecimal(quantity), "version", 0)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=?", Integer.class, cart)).isZero();
    }
    @Test void clientPricesDiscountsAndMissingDeleteRevisionAreRejected() throws Exception {
        var token = token("ADMIN"); var product = activeProduct(token, category(token)); var cart = cart(token);
        for (var field : List.of("unitPrice", "discount", "total", "actorId")) {
            var input = new HashMap<String, Object>(Map.of("quantity", 1, "version", 0)); input.put(field, 0);
            assertThat(send("PUT", itemPath(cart, product), token, input).statusCode()).isEqualTo(400);
        }
        assertThat(send("DELETE", itemPath(cart, product), token, null).statusCode()).isEqualTo(400);
        assertThat(send("DELETE", itemPath(cart, product) + "?version=-1", token, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/carts?page=2147483647&size=100", token, null).statusCode()).isEqualTo(400);
    }
    @Test void concurrentWritersUseAggregateRevisionWithoutLostUpdates() throws Exception {
        var token = token("ADMIN"); var category = category(token); var first = activeProduct(token, category); var second = activeProduct(token, category); var cart = cart(token); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return send("PUT", itemPath(cart, first), token, Map.of("quantity", 1, "version", 0)).statusCode(); });
            var two = pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return send("PUT", itemPath(cart, second), token, Map.of("quantity", 1, "version", 0)).statusCode(); });
            start.countDown(); assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=?", Integer.class, cart)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cart)).isEqualTo(1);
    }
    @Test void moneyAtLongQuantityLimitRemainsExactAndVersionLimitDoesNotWrap() throws Exception {
        var token = token("ADMIN"); var product = activeProduct(token, category(token)); var cart = cart(token); BigDecimal price = new BigDecimal("99999999999999999.99");
        jdbc.update("UPDATE products SET sale_price=? WHERE id=?", price, product);
        var result = send("PUT", itemPath(cart, product), token, Map.of("quantity", Long.MAX_VALUE, "version", 0)); assertThat(result.statusCode()).isEqualTo(200);
        var exactJson = tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        assertThat(exactJson.readTree(result.body()).get("total").decimalValue()).isEqualByComparingTo(price.multiply(BigDecimal.valueOf(Long.MAX_VALUE)));
        jdbc.update("UPDATE carts SET version=? WHERE id=?", Long.MAX_VALUE, cart);
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 1, "version", Long.MAX_VALUE)).statusCode()).isEqualTo(409);
    }
    @Test void auditFailureRollsBackJpaItemVersionAndTotalsAndHistoryIsImmutable() throws Exception {
        var token = token("ADMIN"); var product = activeProduct(token, category(token)); var cart = cart(token); String constraint = "fixture_" + UUID.randomUUID().toString().replace("-", "");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cart_changes WHERE cart_id=?", cart)).isInstanceOf(DataAccessException.class);
        jdbc.execute("ALTER TABLE cart_changes ADD CONSTRAINT " + constraint + " CHECK (kind<>'SET_ITEM') NOT VALID");
        try {
            assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 1, "version", 0)).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cart)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=?", Integer.class, cart)).isZero();
        } finally { jdbc.execute("ALTER TABLE cart_changes DROP CONSTRAINT " + constraint); }
        assertThat(send("PUT", itemPath(cart, product), token, Map.of("quantity", 1, "version", 0)).statusCode()).isEqualTo(200);
    }
}
