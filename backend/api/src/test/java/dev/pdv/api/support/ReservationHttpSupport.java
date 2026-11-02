package dev.pdv.api.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class ReservationHttpSupport extends MovementHttpSupport {
    private final List<UUID> grants = new ArrayList<>();
    @BeforeEach void reservationGrants() {
        for (var action : List.of("read", "write")) {
            UUID grant = UUID.randomUUID(); grants.add(grant);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'reservation',?)", grant, action);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", grant);
        }
    }
    @AfterEach void removeReservationGrants() { for (var id : grants) { jdbc.update("DELETE FROM permissions WHERE id=?", id); } }
    protected UUID stocked(String token, UUID category, long quantity) throws Exception {
        var id = product(token, category);
        org.assertj.core.api.Assertions.assertThat(send("PATCH", "/products/" + id + "/status", token, Map.of("status", "EM_REVISAO", "version", 0)).statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(send("PATCH", "/products/" + id + "/status", token, Map.of("status", "ATIVO", "version", 1)).statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(send("POST", "/inventory/" + id + "/movements", token, command(UUID.randomUUID(), "ENTRY", quantity, 0)).statusCode()).isEqualTo(201);
        return id;
    }
    protected Map<String, Object> reserveItem(UUID id, long quantity, long version) { return Map.of("productId", id, "quantity", quantity, "version", version); }
    protected UUID reserve(String token, UUID product, long quantity) throws Exception {
        var response = send("POST", "/reservations", token, Map.of("requestId", UUID.randomUUID(), "items", List.of(reserveItem(product, quantity, 1))));
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(201); return UUID.fromString(json.readTree(response.body()).get("id").asString());
    }
    protected long reserved(UUID id) { return jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE product_id=?", Long.class, id); }
    protected void dueSoon(UUID id) throws Exception {
        jdbc.update("UPDATE inventory_reservations SET expires_at=clock_timestamp()+interval '150 milliseconds',version=version+1 WHERE id=?", id);
        Thread.sleep(200);
    }
    protected boolean expire(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, id)); }
    protected boolean protect(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT protect_inventory_reservation(?)", Boolean.class, id)); }
}
