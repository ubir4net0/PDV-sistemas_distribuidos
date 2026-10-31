package dev.pdv.api.customers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import dev.pdv.api.support.CustomerHttpSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerLifecycleHttpIT extends CustomerHttpSupport {
    @Override
    protected List<String> actions() { return List.of("create", "read", "update", "status"); }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR"})
    void authorizedRolesReadEditAndPreserveActorAndSnapshots(String role) throws Exception {
        var token = token(role);
        var id = create(token);
        assertThat(send("GET", "/customers/" + id, token, null).statusCode()).isEqualTo(200);
        var changed = send("PUT", "/customers/" + id, token, update(0, "Changed"));
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(changed.body()).get("version").asLong()).isEqualTo(1);
        var history = send("GET", "/customers/" + id + "/history", token, null);
        assertThat(history.statusCode()).isEqualTo(200);
        var event = json.readTree(history.body()).get("items").get(0);
        assertThat(event.get("actorId").asString()).isEqualTo(("ADMIN".equals(role) ? admin : operator).toString());
        assertThat(event.get("before").get("name").asString()).isEqualTo("Synthetic Customer");
        assertThat(event.get("after").get("name").asString()).isEqualTo("Changed");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM customers WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void filtersAndPaginatesWithStableOrderWithoutTreatingWildcardsAsPatterns() throws Exception {
        var token = token("OPERATOR");
        create(token);
        assertThat(send("POST", "/customers", token, input("12ABC34501DE35")).statusCode()).isEqualTo(201);
        var first = json.readTree(send("GET", "/customers?size=1&page=0&name=Synthetic&active=true", token, null).body());
        var second = json.readTree(send("GET", "/customers?size=1&page=1", token, null).body());
        assertThat(first.get("total").asLong()).isEqualTo(2);
        assertThat(first.get("items").size()).isEqualTo(1);
        assertThat(first.get("items").get(0).get("id").asString()).isNotEqualTo(second.get("items").get(0).get("id").asString());
        var exact = json.readTree(send("GET", "/customers?document=123.456.789-09", token, null).body());
        assertThat(exact.get("total").asLong()).isEqualTo(1);
        assertThat(json.readTree(send("GET", "/customers?name=%25", token, null).body()).get("total").asLong()).isZero();
        assertThat(json.readTree(send("GET", "/customers?active=false", token, null).body()).get("total").asLong()).isZero();
    }

    @Test
    void onlyAdminChangesStatusEvenWhenOperatorReceivesGrant() throws Exception {
        var operatorToken = token("OPERATOR");
        var id = create(operatorToken);
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT ur.role_id,p.id FROM user_roles ur CROSS JOIN permissions p
                WHERE ur.user_id=? AND p.resource='customer' AND p.action='status'
                """, operator);
        assertThat(send("PATCH", "/customers/" + id + "/status", operatorToken, Map.of("active", false, "version", 0)).statusCode())
                .isEqualTo(403);
        var adminToken = token("ADMIN");
        assertThat(send("PATCH", "/customers/" + id + "/status", adminToken, Map.of("active", false, "version", 0)).statusCode())
                .isEqualTo(200);
        var inactive = json.readTree(send("GET", "/customers/" + id, operatorToken, null).body());
        assertThat(inactive.get("active").asBoolean()).isFalse();
        assertThat(send("PATCH", "/customers/" + id + "/status", adminToken, Map.of("active", true, "version", 1)).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?", Integer.class, id)).isEqualTo(2);
        assertThat(send("DELETE", "/customers/" + id, adminToken, null).statusCode()).isEqualTo(403);
    }

    @Test
    void concurrentEditsHaveOneWinnerAndOneHistoryRecord() throws Exception {
        var token = token("OPERATOR");
        var id = create(token);
        var first = CompletableFuture.supplyAsync(() -> edit(token, id, "First"));
        var second = CompletableFuture.supplyAsync(() -> edit(token, id, "Second"));
        assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT version FROM customers WHERE id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackJpaMutationAndVersion() throws Exception {
        var token = token("ADMIN");
        var id = create(token);
        String constraint = "audit_it_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE customer_changes ADD CONSTRAINT " + constraint + " CHECK (customer_id <> '" + id + "'::uuid)");
        try {
            assertThat(send("PUT", "/customers/" + id, token, update(0, "Must roll back")).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT name FROM customers WHERE id=?", String.class, id)).isEqualTo("Synthetic Customer");
            assertThat(jdbc.queryForObject("SELECT version FROM customers WHERE id=?", Long.class, id)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE customer_changes DROP CONSTRAINT " + constraint);
        }
    }

    @Test
    void duplicateDocumentUpdateRollsBackWithoutHistory() throws Exception {
        var token = token("ADMIN");
        var id = create(token);
        assertThat(send("POST", "/customers", token, input("12ABC34501DE35")).statusCode()).isEqualTo(201);
        var response = send("PUT", "/customers/" + id, token, Map.of("version", 0, "customer", input("12ABC34501DE35")));
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEqualTo("{\"code\":\"document_conflict\"}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?", Integer.class, id)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/customers?page=-1", "/customers?size=101", "/customers?active=bad",
            "/customers?document=bad", "/customers/not-a-uuid"})
    void invalidQueriesReturnSafe400(String path) throws Exception {
        var response = send("GET", path, token("ADMIN"), null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("{\"code\":\"invalid_request\"}");
    }

    @Test
    void missingObjectsAndAccessChecksAreDistinct() throws Exception {
        var token = token("OPERATOR");
        assertThat(send("GET", "/customers/" + UUID.randomUUID(), token, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/customers", null, null).statusCode()).isEqualTo(401);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT role_id FROM user_roles WHERE user_id=?)", operator);
        assertThat(send("GET", "/customers", token, null).statusCode()).isEqualTo(403);
    }

    private int edit(String token, UUID id, String name) {
        try { return send("PUT", "/customers/" + id, token, update(0, name)).statusCode(); }
        catch (Exception exception) { throw new IllegalStateException("Synthetic edit failed", exception); }
    }

    private Map<String, Object> update(long version, String name) {
        return Map.of("version", version, "customer", Map.of("personType", "PF", "document", "12345678909", "name", name));
    }
}
