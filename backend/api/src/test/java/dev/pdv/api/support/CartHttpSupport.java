package dev.pdv.api.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.ConnectionCallback;

public abstract class CartHttpSupport extends CatalogHttpSupport {
    private final List<UUID> grants = new ArrayList<>();
    @BeforeEach void cartPermissions() {
        for (var action : List.of("read", "write")) {
            UUID grant = UUID.randomUUID(); grants.add(grant);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'cart',?)", grant, action);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE code IN ('ADMIN','OPERATOR')", grant);
        }
    }
    @AfterEach void cleanupCarts() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database = connection.getCatalog();
            if (!(database.matches("pdv_verify_[a-f0-9]{32}") || (database.equals("pdv_ci") && "true".equals(System.getenv("CI"))))) { throw new IllegalStateException("Unowned fixture database"); }
            if (!connection.getAutoCommit()) { throw new IllegalStateException("Fixture transaction must be owned"); }
            try {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) { statement.execute("SET LOCAL session_replication_role=replica"); }
                for (var sql : List.of("DELETE FROM cart_changes WHERE actor_id IN (?,?)", "DELETE FROM cart_operations WHERE actor_id IN (?,?)",
                        "DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE actor_id IN (?,?))", "DELETE FROM carts WHERE actor_id IN (?,?)")) {
                    try (var statement = connection.prepareStatement(sql)) { statement.setObject(1, admin); statement.setObject(2, operator); statement.executeUpdate(); }
                }
                connection.commit();
            } catch (java.sql.SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
            return null;
        });
        for (var grant : grants) { jdbc.update("DELETE FROM permissions WHERE id=?", grant); }
    }
    protected UUID customer() {
        if (!customers.isEmpty()) { return customers.getFirst(); }
        UUID id = jdbc.queryForObject("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic') RETURNING id", UUID.class); customers.add(id); return id;
    }
    protected UUID activeProduct(String token, UUID category) throws Exception {
        UUID id = product(token, category);
        org.assertj.core.api.Assertions.assertThat(send("PATCH", "/products/" + id + "/status", token, Map.of("status", "EM_REVISAO", "version", 0)).statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(send("PATCH", "/products/" + id + "/status", token, Map.of("status", "ATIVO", "version", 1)).statusCode()).isEqualTo(200); return id;
    }
    protected UUID cart(String token) throws Exception {
        var response = send("POST", "/carts", token, Map.of("requestId", UUID.randomUUID(), "customerId", customer()));
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(201); return UUID.fromString(json.readTree(response.body()).get("id").asString());
    }
    protected String itemPath(UUID cart, UUID product) { return "/carts/" + cart + "/items/" + product; }
}
