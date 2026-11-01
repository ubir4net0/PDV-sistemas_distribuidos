package dev.pdv.api.support;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class InventoryHttpSupport extends CatalogHttpSupport {
    private final UUID readPermission = UUID.randomUUID();
    @BeforeEach
    void provisionInventoryPermission() {
        jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'inventory','read')", readPermission);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", readPermission);
    }
    @AfterEach
    void cleanupInventoryPermission() { jdbc.update("DELETE FROM permissions WHERE id=?", readPermission); }
    protected void revokeRead() {
        jdbc.update("DELETE FROM role_permissions WHERE permission_id=?", readPermission);
    }
    protected List<UUID> actors() { return List.of(admin, operator); }
}
