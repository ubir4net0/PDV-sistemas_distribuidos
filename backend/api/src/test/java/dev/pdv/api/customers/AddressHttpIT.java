package dev.pdv.api.customers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import dev.pdv.api.support.CustomerHttpSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AddressHttpIT extends CustomerHttpSupport {
    @Override
    protected List<String> actions() { return List.of("create", "read", "update", "status", "address_write"); }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR"})
    void rolesManageAddressesPrincipalAndHistoryWithoutLostUpdates(String role) throws Exception {
        var token = token(role);
        var customer = create(token);
        UUID first = address(customer, token, true, "First");
        UUID second = address(customer, token, false, "Second");
        var page = json.readTree(send("GET", "/customers/" + customer + "/addresses?size=1", token, null).body());
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("id").asString()).isEqualTo(first.toString());
        assertThat(page.get("items").get(0).get("postalCode").asString()).isEqualTo("69000000");
        var update = send("PUT", "/customers/" + customer + "/addresses/" + second, token,
                Map.of("version", 0, "address", body(true, "Changed", "AM", "69000-000")));
        assertThat(update.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT id FROM customer_addresses WHERE customer_id=? AND is_primary", UUID.class, customer))
                .isEqualTo(second);
        assertThat(send("PUT", "/customers/" + customer + "/addresses/" + first, token,
                Map.of("version", 0, "address", body(true, "Stale", "AM", "69000-000"))).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?", Integer.class, customer)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT version FROM customers WHERE id=?", Long.class, customer)).isEqualTo(3);
        var history = json.readTree(send("GET", "/customers/" + customer + "/history", token, null).body());
        var event = history.get("items").get(2);
        assertThat(event.get("actorId").asString()).isEqualTo(("ADMIN".equals(role) ? admin : operator).toString());
        assertThat(event.get("after").get("addresses").size()).isEqualTo(2);
        assertThat(send("DELETE", "/customers/" + customer + "/addresses/" + first, token, null).statusCode()).isEqualTo(403);
    }

    @Test
    void zeroPrincipalIsAllowedAndRegionRemainsUnknown() throws Exception {
        var token = token("OPERATOR");
        var customer = create(token);
        address(customer, token, false, "No principal");
        var regions = json.readTree(send("GET", "/customers/regions", token, null).body());
        assertThat(regions.size()).isEqualTo(1);
        assertThat(regions.get(0).get("state").isNull()).isTrue();
        assertThat(regions.get(0).get("city").isNull()).isTrue();
        assertThat(regions.get(0).get("customers").asLong()).isEqualTo(1);
    }

    @Test
    void regionalQueryCountsEachCustomerOnceUsingOnlyPrincipalAndFilters() throws Exception {
        var token = token("ADMIN");
        var first = create(token);
        address(first, token, true, "Primary");
        address(first, token, false, "Secondary");
        var created = send("POST", "/customers", token, input("12ABC34501DE35"));
        assertThat(created.statusCode()).isEqualTo(201);
        UUID second = UUID.fromString(json.readTree(created.body()).get("id").asString());
        address(second, token, true, "Other customer");
        var regions = json.readTree(send("GET", "/customers/regions?state=AM&city=manaus", token, null).body());
        assertThat(regions.size()).isEqualTo(1);
        assertThat(regions.get(0).get("customers").asLong()).isEqualTo(2);
        assertThat(send("PATCH", "/customers/" + second + "/status", token, Map.of("version", 1, "active", false)).statusCode())
                .isEqualTo(200);
        var active = json.readTree(send("GET", "/customers/regions?active=true", token, null).body());
        assertThat(active.get(0).get("customers").asLong()).isEqualTo(1);
        assertThat(send("GET", "/customers/regions?state=XX", token, null).statusCode()).isEqualTo(400);
    }

    @Test
    void concurrentPrimarySelectionsBothCommitAndKeepExactlyOnePrincipal() throws Exception {
        var token = token("OPERATOR");
        var customer = create(token);
        var first = CompletableFuture.supplyAsync(() -> createStatus(customer, token, "First"));
        var second = CompletableFuture.supplyAsync(() -> createStatus(customer, token, "Second"));
        assertThat(List.of(first.get(), second.get())).containsExactly(201, 201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id=?", Integer.class, customer)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id=? AND is_primary", Integer.class, customer))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?", Integer.class, customer)).isEqualTo(2);
    }

    @Test
    void auditFailureRollsBackInsertDemotionAndParentVersionTogether() throws Exception {
        var token = token("ADMIN");
        var customer = create(token);
        UUID original = address(customer, token, true, "Original");
        String constraint = "address_it_" + UUID.randomUUID().toString().replace("-", "");
        // Keep the previously committed audit row; reject only the next write in this owned test database.
        jdbc.execute("ALTER TABLE customer_changes ADD CONSTRAINT " + constraint + " CHECK (customer_id <> '" + customer + "'::uuid) NOT VALID");
        try {
            assertThat(send("POST", "/customers/" + customer + "/addresses", token, body(true, "Rollback", "AM", "69000-000"))
                    .statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id=?", Integer.class, customer)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT id FROM customer_addresses WHERE customer_id=? AND is_primary", UUID.class, customer))
                    .isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT version FROM customers WHERE id=?", Long.class, customer)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM customer_addresses WHERE id=?", Long.class, original)).isZero();
        } finally { jdbc.execute("ALTER TABLE customer_changes DROP CONSTRAINT " + constraint); }
    }

    @Test
    void crossCustomerAddressAndMissingCustomerReturn404() throws Exception {
        var token = token("ADMIN");
        var first = create(token);
        UUID address = address(first, token, false, "First");
        var created = send("POST", "/customers", token, input("12ABC34501DE35"));
        assertThat(created.statusCode()).isEqualTo(201);
        String second = json.readTree(created.body()).get("id").asString();
        assertThat(send("PUT", "/customers/" + second + "/addresses/" + address, token,
                Map.of("version", 0, "address", body(true, "Wrong parent", "AM", "69000-000"))).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/customers/" + UUID.randomUUID() + "/addresses", token,
                body(true, "Missing parent", "AM", "69000-000")).statusCode()).isEqualTo(404);
    }

    @Test
    void authenticationAndExplicitAddressPermissionAreRequired() throws Exception {
        var token = token("OPERATOR");
        var customer = create(token);
        String path = "/customers/" + customer + "/addresses";
        assertThat(send("GET", path, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/customers/regions", null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", path, null, body(false, "Denied", "AM", "69000-000")).statusCode()).isEqualTo(401);
        jdbc.update("""
                DELETE FROM role_permissions WHERE role_id IN (SELECT role_id FROM user_roles WHERE user_id=?)
                AND permission_id IN (SELECT id FROM permissions WHERE resource='customer' AND action='address_write')
                """, operator);
        assertThat(send("POST", path, token, body(false, "Denied", "AM", "69000-000")).statusCode()).isEqualTo(403);
        assertThat(send("GET", path, token, null).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @CsvSource({"XX,69000-000", "AM,invalid", "am,69000-000", "AM,690000000"})
    void invalidPostalCodeOrStateDoesNotCreateAddress(String state, String postalCode) throws Exception {
        var token = token("ADMIN");
        var customer = create(token);
        var response = send("POST", "/customers/" + customer + "/addresses", token, body(true, "Invalid", state, postalCode));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("{\"code\":\"invalid_request\"}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id=?", Integer.class, customer)).isZero();
    }

    private UUID address(UUID customer, String token, boolean main, String street) throws Exception {
        var response = send("POST", "/customers/" + customer + "/addresses", token, body(main, street, "AM", "69000-000"));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).get("id").asString());
    }

    private int createStatus(UUID customer, String token, String street) {
        try { return send("POST", "/customers/" + customer + "/addresses", token, body(true, street, "AM", "69000-000")).statusCode(); }
        catch (Exception exception) { throw new IllegalStateException("Synthetic address request failed", exception); }
    }

    private Map<String, Object> body(boolean main, String street, String state, String postalCode) {
        return Map.of("type", "Teste", "street", street, "number", "1", "neighborhood", "Centro",
                "city", "Manaus", "state", state, "postalCode", postalCode, "primary", main);
    }
}
