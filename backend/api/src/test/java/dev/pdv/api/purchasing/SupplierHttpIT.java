package dev.pdv.api.purchasing;

import dev.pdv.api.support.SupplierHttpSupport;
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

class SupplierHttpIT extends SupplierHttpSupport {
    @Test
    void administratorManagesCanonicalSupplierWithHistoryAndLogicalDelete() throws Exception {
        var token = token("ADMIN"); var id = supplier(token, "12.abc.345/01de-35");
        var read = json.readTree(send("GET", "/suppliers/" + id, token("OPERATOR"), null).body());
        assertThat(read.get("cnpj").asString()).isEqualTo("12ABC34501DE35");
        assertThat(send("POST", "/suppliers", token, Map.of("cnpj", "12ABC34501DE35", "legalName", "Duplicate")).statusCode()).isEqualTo(409);
        var body = Map.of("supplier", Map.of("cnpj", "12ABC34501DE35", "legalName", "Renamed", "email", "synthetic@example.test"), "version", 0);
        assertThat(send("PUT", "/suppliers/" + id, token, body).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/suppliers/" + id, token, body).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", "/suppliers/" + id + "?version=1", token, null).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT active FROM suppliers WHERE id=?", Boolean.class, id)).isFalse();
        var history = json.readTree(send("GET", "/suppliers/" + id + "/history", token("OPERATOR"), null).body()).get("items");
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(1).get("actorId").asString()).isEqualTo(admin.toString());
        assertThat(history.get(1).get("before").get("legal_name").asString()).isEqualTo("Synthetic Supplier");
        assertThat(history.get(1).get("after").get("legal_name").asString()).isEqualTo("Renamed");
        assertThat(json.readTree(send("GET", "/suppliers?active=false&size=1", token, null).body()).get("total").asLong()).isEqualTo(1);
        assertThat(send("PATCH", "/suppliers/" + id + "/status", token, Map.of("active", true, "version", 2)).statusCode()).isEqualTo(200);
    }
    @Test
    void productHasOptionalSingleSupplierAndInactiveLinksArePreserved() throws Exception {
        var token = token("ADMIN"); var category = category(token); var product = product(token, category); String path = "/products/" + product + "/supplier";
        var first = supplier(token, "12ABC34501DE35"); var second = supplier(token, "11222333000181");
        assertThat(json.readTree(send("GET", "/products/" + product, token, null).body()).get("supplierId").isNull()).isTrue();
        assertThat(send("PUT", path, token, Map.of("supplierId", first, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(send("PATCH", "/suppliers/" + first + "/status", token, Map.of("active", false, "version", 0)).statusCode()).isEqualTo(200);
        assertThat(send("PUT", path, token, Map.of("supplierId", first, "version", 1)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT supplier_id FROM products WHERE id=?", UUID.class, product)).isEqualTo(first);
        assertThat(json.readTree(send("GET", "/suppliers/" + first + "/products", token("OPERATOR"), null).body()).get("total").asLong()).isEqualTo(1);
        assertThat(send("PUT", "/products/" + product, token, Map.of("product", productInput(category, "PRESERVED-LINK"), "version", 1)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT supplier_id FROM products WHERE id=?", UUID.class, product)).isEqualTo(first);
        assertThat(send("PUT", path, token, Map.of("supplierId", second, "version", 2)).statusCode()).isEqualTo(200);
        assertThat(send("PUT", path, token, Map.of("supplierId", first, "version", 3)).statusCode()).isEqualTo(409);
        var unlink = new HashMap<String, Object>(); unlink.put("supplierId", null); unlink.put("version", 3);
        assertThat(send("PUT", path, token, unlink).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT supplier_id FROM products WHERE id=?", UUID.class, product)).isNull();
        var history = json.readTree(send("GET", "/products/" + product + "/history", token, null).body()).get("items");
        assertThat(history.get(history.size() - 1).get("action").asString()).isEqualTo("SUPPLIER_CHANGED");
        assertThat(history.get(history.size() - 1).get("before").get("supplier_id").asString()).isEqualTo(second.toString());
        assertThat(history.get(history.size() - 1).get("after").get("supplier_id").isNull()).isTrue();
    }
    @Test
    void readsNeedCurrentPermissionAndOperatorCannotWriteEvenWithAccidentalGrant() throws Exception {
        var token = token("ADMIN"); var operator = token("OPERATOR"); var supplier = supplier(token, "11222333000181"); var product = product(token, category(token));
        for (String path : List.of("/suppliers", "/suppliers/" + supplier, "/suppliers/" + supplier + "/history", "/suppliers/" + supplier + "/products")) {
            assertThat(send("GET", path, null, null).statusCode()).isEqualTo(401); assertThat(send("GET", path, operator, null).statusCode()).isEqualTo(200);
        }
        var requests = List.of(new Object[]{"POST", "/suppliers", Map.of("cnpj", "12ABC34501DE35", "legalName", "Denied")},
                new Object[]{"PUT", "/suppliers/" + supplier, Map.of("supplier", Map.of("cnpj", "11222333000181", "legalName", "Denied"), "version", 0)},
                new Object[]{"PATCH", "/suppliers/" + supplier + "/status", Map.of("active", false, "version", 0)},
                new Object[]{"DELETE", "/suppliers/" + supplier + "?version=0", null},
                new Object[]{"PUT", "/products/" + product + "/supplier", Map.of("supplierId", supplier, "version", 0)});
        for (var request : requests) {
            assertThat(send((String) request[0], (String) request[1], operator, request[2]).statusCode()).isEqualTo(403);
            assertThat(send((String) request[0], (String) request[1], null, request[2]).statusCode()).isEqualTo(401);
        }
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='supplier')");
        assertThat(send("GET", "/suppliers", token, null).statusCode()).isEqualTo(403);
        assertThat(send("PUT", "/products/" + product + "/supplier", token, Map.of("supplierId", supplier, "version", 0)).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest
    @ValueSource(strings = {"11222333000180", "12345678909", "00000000000000", "12.ABC.345/01DE-34", "12ABC34501Dß35"})
    void rejectsInvalidCnpj(String cnpj) throws Exception { assertThat(send("POST", "/suppliers", token("ADMIN"), Map.of("cnpj", cnpj, "legalName", "Synthetic")).statusCode()).isEqualTo(400); }
    @Test
    void duplicateCnpjRaceHasOneWinner() throws Exception {
        var token = token("ADMIN"); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> insert = () -> { start.await(5, TimeUnit.SECONDS); return send("POST", "/suppliers", token, Map.of("cnpj", "11222333000181", "legalName", "Race")).statusCode(); };
            var first = executor.submit(insert); var second = executor.submit(insert); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
    }
    @Test
    void concurrentSupplierChangesRejectStaleVersion() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var first = supplier(token, "11222333000181"); var second = supplier(token, "12ABC34501DE35");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return send("PUT", "/products/" + product + "/supplier", token, Map.of("supplierId", first, "version", 0)).statusCode(); });
            var two = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return send("PUT", "/products/" + product + "/supplier", token, Map.of("supplierId", second, "version", 0)).statusCode(); }); start.countDown();
            assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id=?", Long.class, product)).isEqualTo(1);
    }
    @Test
    void concurrentInactivationAndAssociationSerializeWithoutCreatingAnInactiveNewLink() throws Exception {
        var token = token("ADMIN"); var supplier = supplier(token, "11222333000181"); var product = product(token, category(token));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var link = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return send("PUT", "/products/" + product + "/supplier", token, Map.of("supplierId", supplier, "version", 0)).statusCode(); });
            var inactive = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return send("PATCH", "/suppliers/" + supplier + "/status", token, Map.of("active", false, "version", 0)).statusCode(); }); start.countDown();
            int linked = link.get(30, TimeUnit.SECONDS); assertThat(inactive.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(linked).isIn(200, 409);
            assertThat(jdbc.queryForObject("SELECT supplier_id FROM products WHERE id=?", UUID.class, product)).isEqualTo(linked == 200 ? supplier : null);
        }
        assertThat(jdbc.queryForObject("SELECT active FROM suppliers WHERE id=?", Boolean.class, supplier)).isFalse();
    }
    @Test
    void linkAndSupplierChangesRollbackWhenTheirAuditFails() throws Exception {
        var token = token("ADMIN"); var supplier = supplier(token, "11222333000181"); var product = product(token, category(token));
        String constraint = "reject_supplier_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE supplier_changes ADD CONSTRAINT " + constraint + " CHECK (supplier_id <> '" + supplier + "'::uuid) NOT VALID");
        try {
            assertThat(send("PATCH", "/suppliers/" + supplier + "/status", token, Map.of("active", false, "version", 0)).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT active FROM suppliers WHERE id=?", Boolean.class, supplier)).isTrue();
            assertThat(jdbc.queryForObject("SELECT version FROM suppliers WHERE id=?", Long.class, supplier)).isZero();
        } finally { jdbc.execute("ALTER TABLE supplier_changes DROP CONSTRAINT " + constraint); }
        jdbc.execute("ALTER TABLE catalog_changes ADD CONSTRAINT " + constraint + " CHECK (product_id <> '" + product + "'::uuid) NOT VALID");
        try {
            assertThat(send("PUT", "/products/" + product + "/supplier", token, Map.of("supplierId", supplier, "version", 0)).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT supplier_id FROM products WHERE id=?", UUID.class, product)).isNull();
            assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id=?", Long.class, product)).isZero();
        } finally { jdbc.execute("ALTER TABLE catalog_changes DROP CONSTRAINT " + constraint); }
    }
    @Test
    void missingReferencesAndInvalidVersionsOrPaginationAreRejected() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token));
        assertThat(send("PUT", "/products/" + product + "/supplier", token, Map.of("supplierId", UUID.randomUUID(), "version", 0)).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/suppliers/" + UUID.randomUUID(), token, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/suppliers?size=101", token, null).statusCode()).isEqualTo(400);
        assertThat(send("DELETE", "/suppliers/" + UUID.randomUUID() + "?version=-1", token, null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/suppliers", token, Map.of("cnpj", "11222333000181", "legalName", "Synthetic", "email", "bad")).statusCode()).isEqualTo(400);
    }
}
