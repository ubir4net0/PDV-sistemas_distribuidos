package dev.pdv.api.support;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class MovementHttpSupport extends InventoryHttpSupport {
    private final UUID writePermission = UUID.randomUUID();
    @BeforeEach
    void provisionWritePermission() {
        jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'inventory','write')", writePermission);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", writePermission);
    }
    @AfterEach void cleanupWritePermission() { jdbc.update("DELETE FROM permissions WHERE id=?", writePermission); }
    protected void revokeWrite() { jdbc.update("DELETE FROM role_permissions WHERE permission_id=?", writePermission); }
    protected Map<String, Object> command(UUID request, String type, long quantity, long version) {
        return Map.of("requestId", request, "type", type, "quantity", quantity, "reason", "Synthetic movement", "version", version);
    }
    protected Map<String, Object> item(UUID product, String type, long quantity, long version) {
        return Map.of("productId", product, "type", type, "quantity", quantity, "reason", "Synthetic movement", "version", version);
    }
    protected long physical(UUID product) { return jdbc.queryForObject("SELECT physical FROM inventory_balances WHERE product_id=?", Long.class, product); }
}
