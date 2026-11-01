package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import dev.pdv.api.inventory.application.InventoryModels.Opening;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryStore {
    private final JdbcTemplate jdbc;
    public InventoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Balance require(UUID id) {
        var rows = jdbc.query("SELECT *,physical-reserved AS available FROM inventory_balances WHERE product_id=?", InventoryStore::balance, id);
        if (rows.isEmpty()) { throw new ApiFailure(404, "inventory_not_found"); } return rows.getFirst();
    }
    public Opening opening(UUID id) {
        var rows = jdbc.query("SELECT * FROM inventory_openings WHERE product_id=?", (row, index) -> new Opening(row.getObject("product_id", UUID.class),
                row.getString("source"), row.getLong("physical"), row.getLong("reserved"), row.getLong("version"), row.getTimestamp("recorded_at").toInstant()), id);
        if (rows.isEmpty()) { throw new ApiFailure(404, "inventory_not_found"); } return rows.getFirst();
    }
    public Page<Balance> list(int page, int size) {
        var rows = jdbc.query("SELECT *,physical-reserved AS available FROM inventory_balances ORDER BY product_id LIMIT ? OFFSET ?", InventoryStore::balance, size, (long) page * size);
        return new Page<>(rows, jdbc.queryForObject("SELECT count(*) FROM inventory_balances", Long.class), page, size);
    }
    static Balance balance(ResultSet row, int index) throws SQLException {
        return new Balance(row.getObject("product_id", UUID.class), row.getLong("physical"), row.getLong("reserved"), row.getLong("available"),
                row.getLong("version"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
