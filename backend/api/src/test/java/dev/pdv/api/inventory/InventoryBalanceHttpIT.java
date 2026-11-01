package dev.pdv.api.inventory;

import dev.pdv.api.support.InventoryHttpSupport;
import dev.pdv.api.support.InventoryFixtureCleanup;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryBalanceHttpIT extends InventoryHttpSupport {
    @Test
    void newProductHasZeroBalanceAndImmutableTechnicalOpening() throws Exception {
        var token = token("ADMIN"); var id = product(token, category(token));
        var data = json.readTree(send("GET", "/inventory/" + id, token("OPERATOR"), null).body());
        assertThat(data.get("productId").asString()).isEqualTo(id.toString());
        for (String field : List.of("physical", "reserved", "available", "version")) { assertThat(data.get(field).asLong()).isZero(); }
        var opening = json.readTree(send("GET", "/inventory/" + id + "/opening", token, null).body());
        assertThat(opening.get("source").asString()).isEqualTo("ZERO_INITIALIZATION");
        assertThat(opening.get("recordedAt").asString()).isNotBlank();
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_openings SET source=source WHERE product_id=?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_openings WHERE product_id=?", id)).isInstanceOf(DataAccessException.class);
    }
    @Test
    void availableIsCalculatedAndMetadataDoesNotChangeStockVersion() throws Exception {
        var token = token("ADMIN"); var category = category(token); var id = product(token, category);
        // Synthetic reserved value only: reservation operations belong to PDV-029.
        jdbc.update("UPDATE inventory_balances SET physical=10,reserved=3,version=1 WHERE product_id=?", id);
        assertThat(send("PUT", "/products/" + id, token, Map.of("product", productInput(category, "RENAMED"), "version", 0)).statusCode()).isEqualTo(200);
        var result = json.readTree(send("GET", "/inventory/" + id, token, null).body());
        assertThat(result.get("physical").asLong()).isEqualTo(10); assertThat(result.get("reserved").asLong()).isEqualTo(3);
        assertThat(result.get("available").asLong()).isEqualTo(7); assertThat(result.get("version").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT physical FROM inventory_openings WHERE product_id=?", Long.class, id)).isZero();
    }
    @Test
    void readsRequireRoleAndCurrentGrantAndWritesAreDenied() throws Exception {
        var token = token("ADMIN"); var id = product(token, category(token));
        for (String path : List.of("/inventory", "/inventory/" + id, "/inventory/" + id + "/opening")) {
            assertThat(send("GET", path, null, null).statusCode()).isEqualTo(401);
            for (String role : List.of("ADMIN", "OPERATOR")) { assertThat(send("GET", path, token(role), null).statusCode()).isEqualTo(200); }
            assertThat(send("PUT", path, token, Map.of("physical", 100)).statusCode()).isEqualTo(403);
        }
        revokeRead(); assertThat(send("GET", "/inventory/" + id, token, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/inventory/" + id + "/opening", token, null).statusCode()).isEqualTo(403);
    }
    @Test
    void pagesAreDeterministicAndUnknownProductIsNotFound() throws Exception {
        var token = token("ADMIN"); var category = category(token); product(token, category); product(token, category);
        var first = json.readTree(send("GET", "/inventory?size=1", token, null).body());
        var second = json.readTree(send("GET", "/inventory?size=1&page=1", token, null).body());
        assertThat(first.get("total").asLong()).isEqualTo(2);
        assertThat(first.get("items").get(0).get("productId").asString()).isNotEqualTo(second.get("items").get(0).get("productId").asString());
        assertThat(send("GET", "/inventory/" + UUID.randomUUID(), token, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/inventory/" + UUID.randomUUID() + "/opening", token, null).statusCode()).isEqualTo(404);
    }
    @ParameterizedTest
    @ValueSource(strings = {"/inventory?size=101", "/inventory?page=-1", "/inventory?size=0", "/inventory?page=2147483647&size=100", "/inventory/not-a-uuid"})
    void invalidInputIsBadRequest(String path) throws Exception { assertThat(send("GET", path, token("ADMIN"), null).statusCode()).isEqualTo(400); }
    @Test
    void failedOpeningRollsBackProductAndBalanceAndFixtureCleanupRestoresGuards() throws Exception {
        var token = token("ADMIN"); var category = category(token); String constraint = "reject_opening_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE inventory_openings ADD CONSTRAINT " + constraint + " CHECK (source='REJECT') NOT VALID");
        try {
            assertThat(send("POST", "/products", token, productInput(category, "ROLLBACK")).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE category_id=?", Integer.class, category)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_balances", Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE inventory_openings DROP CONSTRAINT " + constraint); }
        var first = product(token, category); InventoryFixtureCleanup.clean(jdbc, List.of(first), actors());
        var second = product(token, category);
        assertThat(jdbc.queryForObject("SHOW session_replication_role", String.class)).isEqualTo("origin");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_openings WHERE product_id=?", second)).isInstanceOf(DataAccessException.class);
    }
}
