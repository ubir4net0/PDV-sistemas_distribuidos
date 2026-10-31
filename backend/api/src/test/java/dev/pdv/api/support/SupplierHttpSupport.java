package dev.pdv.api.support;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class SupplierHttpSupport extends CatalogHttpSupport {
    protected final List<UUID> suppliers = new ArrayList<>();
    private final List<UUID> supplierPermissions = new ArrayList<>();
    @BeforeEach
    void provisionSupplierPermissions() {
        for (String action : List.of("read", "write")) {
            UUID id = UUID.randomUUID(); supplierPermissions.add(id);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'supplier',?)", id, action);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", id);
        }
    }
    @AfterEach
    void cleanupSuppliers() {
        for (var id : products) { jdbc.update("UPDATE products SET supplier_id=NULL WHERE id=?", id); }
        for (var id : suppliers) { jdbc.update("DELETE FROM supplier_changes WHERE supplier_id=?", id); jdbc.update("DELETE FROM suppliers WHERE id=?", id); }
        for (var id : supplierPermissions) { jdbc.update("DELETE FROM permissions WHERE id=?", id); }
    }
    @Override
    protected HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var response = super.send(method, path, token, body);
        if ("POST".equals(method) && "/suppliers".equals(path) && response.statusCode() == 201) {
            synchronized (this) { suppliers.add(UUID.fromString(json.readTree(response.body()).get("id").asString())); }
        }
        return response;
    }
    protected UUID supplier(String token, String cnpj) throws Exception {
        var result = send("POST", "/suppliers", token, Map.of("cnpj", cnpj, "legalName", "Synthetic Supplier"));
        org.assertj.core.api.Assertions.assertThat(result.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(result.body()).get("id").asString());
    }
}
