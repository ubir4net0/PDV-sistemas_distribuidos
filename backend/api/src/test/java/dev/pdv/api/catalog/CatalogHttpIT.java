package dev.pdv.api.catalog;

import dev.pdv.api.support.CatalogHttpSupport;
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

import static org.assertj.core.api.Assertions.assertThat;

class CatalogHttpIT extends CatalogHttpSupport {
    @Test
    void administratorCreatesExactDecimalCanonicalSkuAndAuditedRecords() throws Exception {
        var token = token("ADMIN"); var category = category(token);
        var response = send("POST", "/products", token, productInput(category, " abc-01 "));
        assertThat(response.statusCode()).isEqualTo(201);
        var data = json.readTree(response.body()); var id = UUID.fromString(data.get("id").asString());
        assertThat(data.get("sku").asString()).isEqualTo("ABC-01");
        assertThat(data.get("salePrice").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(data.get("status").asString()).isEqualTo("CADASTRADO");
        assertThat(send("POST", "/products", token, productInput(category, "ABC-01")).statusCode()).isEqualTo(409);
        var history = json.readTree(send("GET", "/products/" + id + "/history", token("OPERATOR"), null).body()).get("items");
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("actorId").asString()).isEqualTo(admin.toString());
        assertThat(history.get(0).get("before").size()).isZero();
        assertThat(history.get(0).get("after").get("sku").asString()).isEqualTo("ABC-01");
    }
    @Test
    void onlyApprovedTransitionsAndVersionsAreAcceptedAndEditsConserveState() throws Exception {
        var token = token("ADMIN"); var category = category(token); var id = product(token, category); String path = "/products/" + id;
        assertThat(send("PATCH", path + "/status", token, Map.of("status", "ATIVO", "version", 0)).statusCode()).isEqualTo(409);
        long version = 0;
        for (String state : List.of("EM_REVISAO", "ATIVO", "INATIVO", "ATIVO")) {
            var response = send("PATCH", path + "/status", token, Map.of("status", state, "version", version));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).get("version").asLong()).isEqualTo(++version);
        }
        assertThat(send("PUT", path, token, Map.of("product", productInput(category, "EDITED"), "version", 0)).statusCode()).isEqualTo(409);
        var updated = send("PUT", path, token, Map.of("product", productInput(category, "EDITED"), "version", version));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(updated.body()).get("status").asString()).isEqualTo("ATIVO");
        assertThat(send("PATCH", path + "/status", token, Map.of("status", "CADASTRADO", "version", version + 1)).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", path, token, null).statusCode()).isEqualTo(403);
    }
    @Test
    void inactiveCategoryPreservesExistingProductsButRejectsNewAssociations() throws Exception {
        var token = token("ADMIN"); var first = category(token); var second = category(token); var id = product(token, first);
        assertThat(send("PATCH", "/categories/" + first + "/status", token, Map.of("active", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/products", token, productInput(first, "NEW")).statusCode()).isEqualTo(409);
        assertThat(send("PUT", "/products/" + id, token, Map.of("product", productInput(first, "EXISTING"), "version", 0)).statusCode()).isEqualTo(200);
        var other = product(token, second);
        assertThat(send("PUT", "/products/" + other, token, Map.of("product", productInput(first, "OTHER"), "version", 0)).statusCode()).isEqualTo(409);
        assertThat(send("GET", "/categories/" + first, token("OPERATOR"), null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/categories/" + first, token, Map.of("category", Map.of("name", "Renamed"), "version", 1)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT category_id FROM products WHERE id=?", UUID.class, id)).isEqualTo(first);
    }
    @Test
    void allRoutesRequireAuthenticationAndOperatorCannotWriteEvenWithGrant() throws Exception {
        var token = token("ADMIN"); var category = category(token); var product = product(token, category); var operator = token("OPERATOR");
        var paths = Map.of("/categories/" + category, Map.of("category", Map.of("name", "Changed"), "version", 0),
                "/products/" + product, Map.of("product", productInput(category, "CHANGED"), "version", 0));
        for (var entry : paths.entrySet()) {
            assertThat(send("GET", entry.getKey(), null, null).statusCode()).isEqualTo(401);
            assertThat(send("GET", entry.getKey(), operator, null).statusCode()).isEqualTo(200);
            assertThat(send("GET", entry.getKey() + "/history", null, null).statusCode()).isEqualTo(401);
            assertThat(send("PUT", entry.getKey(), operator, entry.getValue()).statusCode()).isEqualTo(403);
            assertThat(send("PUT", entry.getKey(), null, entry.getValue()).statusCode()).isEqualTo(401);
        }
        for (String path : List.of("/categories", "/products")) {
            Object input = path.equals("/categories") ? Map.of("name", "Other") : productInput(category, "OTHER");
            assertThat(send("POST", path, null, input).statusCode()).isEqualTo(401);
            assertThat(send("POST", path, operator, input).statusCode()).isEqualTo(403);
        }
        assertThat(send("PATCH", "/categories/" + category + "/status", operator, Map.of("active", false, "version", 0)).statusCode()).isEqualTo(403);
        assertThat(send("PATCH", "/products/" + product + "/status", operator, Map.of("status", "EM_REVISAO", "version", 0)).statusCode()).isEqualTo(403);
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='catalog')");
        assertThat(send("GET", "/products/" + product, token, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/categories", token, Map.of("name", "Denied")).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest
    @ValueSource(strings = {"salePrice:-1", "salePrice:1.001", "cost:0.001", "sku:ß", "minimumStock:-1", "unit: "})
    void rejectsInvalidInputWithoutRoundingOrUnicodeSkuExpansion(String example) throws Exception {
        var token = token("ADMIN"); var category = category(token);
        var input = new HashMap<>(productInput(category, "VALID")); var values = example.split(":", 2); input.put(values[0], values[1]);
        assertThat(send("POST", "/products", token, input).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE category_id=?", Integer.class, category)).isZero();
    }
    @Test
    void concurrentSkuCreatesHaveOneWinner() throws Exception {
        var token = token("ADMIN"); var category = category(token); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> { start.await(5, TimeUnit.SECONDS); return send("POST", "/products", token, productInput(category, "RACE")).statusCode(); };
            var first = executor.submit(create); var second = executor.submit(create); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE category_id=?", Integer.class, category)).isEqualTo(1);
    }
    @Test
    void auditFailureRollsBackProductMutationAndVersion() throws Exception {
        var token = token("ADMIN"); var category = category(token); var id = product(token, category);
        String constraint = "reject_catalog_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE catalog_changes ADD CONSTRAINT " + constraint + " CHECK (product_id <> '" + id + "'::uuid) NOT VALID");
        try {
            assertThat(send("PUT", "/products/" + id, token, Map.of("product", productInput(category, "ROLLBACK"), "version", 0)).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id=?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT sku FROM products WHERE id=?", String.class, id)).isNotEqualTo("ROLLBACK");
        } finally { jdbc.execute("ALTER TABLE catalog_changes DROP CONSTRAINT " + constraint); }
    }
    @Test
    void concurrentVersionedChangesHaveOneWinnerAndMissingIdsAreNotFound() throws Exception {
        var token = token("ADMIN"); var category = category(token); var id = product(token, category); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> change = () -> { start.await(5, TimeUnit.SECONDS); return send("PATCH", "/products/" + id + "/status", token, Map.of("status", "EM_REVISAO", "version", 0)).statusCode(); };
            var first = executor.submit(change); var second = executor.submit(change); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(send("GET", "/products/" + UUID.randomUUID(), token, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/categories/" + UUID.randomUUID(), token, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/products/" + id + "/history?size=101", token, null).statusCode()).isEqualTo(400);
    }
}
