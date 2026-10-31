package dev.pdv.api.support;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class CatalogHttpSupport extends CustomerHttpSupport {
    protected final List<UUID> categories = new ArrayList<>();
    protected final List<UUID> products = new ArrayList<>();
    private final List<UUID> catalogPermissions = new ArrayList<>();
    @Override protected List<String> actions() { return List.of(); }
    @BeforeEach
    void catalogPermissions() {
        for (String action : List.of("read", "write")) {
            UUID id = UUID.randomUUID(); catalogPermissions.add(id);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'catalog',?)", id, action);
            // Give both roles every grant: the role guard must still prevent operator writes.
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", id);
        }
    }
    @AfterEach
    void cleanupCatalog() {
        for (var id : products) { jdbc.update("DELETE FROM catalog_changes WHERE product_id=?", id); jdbc.update("DELETE FROM products WHERE id=?", id); }
        for (var id : categories) { jdbc.update("DELETE FROM catalog_changes WHERE category_id=?", id); jdbc.update("DELETE FROM categories WHERE id=?", id); }
        for (var id : catalogPermissions) { jdbc.update("DELETE FROM permissions WHERE id=?", id); }
    }
    @Override
    protected HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var response = super.send(method, path, token, body);
        if ("POST".equals(method) && response.statusCode() == 201 && ("/products".equals(path) || "/categories".equals(path))) {
            UUID id = UUID.fromString(json.readTree(response.body()).get("id").asString());
            synchronized (this) { ("/products".equals(path) ? products : categories).add(id); }
        }
        return response;
    }
    protected UUID category(String token) throws Exception {
        var result = send("POST", "/categories", token, Map.of("name", "Synthetic Category"));
        org.assertj.core.api.Assertions.assertThat(result.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(result.body()).get("id").asString());
    }
    protected Map<String, Object> productInput(UUID category, String sku) {
        return Map.of("categoryId", category, "sku", sku, "name", "Synthetic Product", "salePrice", "12.34", "cost", "4.56", "unit", "UN", "minimumStock", 0);
    }
    protected UUID product(String token, UUID category) throws Exception {
        var response = send("POST", "/products", token, productInput(category, UUID.randomUUID().toString()));
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).get("id").asString());
    }
}
