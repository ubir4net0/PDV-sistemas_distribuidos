package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import dev.pdv.api.inventory.application.MovementModels.Item;
import dev.pdv.api.inventory.application.MovementModels.Movement;
import dev.pdv.api.inventory.application.MovementModels.Operation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class MovementStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public MovementStore(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }
    public void lockRequest(UUID actor, UUID request) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (row, index) -> 0, "inventory:" + actor + ":" + request);
    }
    public Optional<Operation> replay(UUID actor, UUID request, String fingerprint) {
        var rows = jdbc.query("SELECT fingerprint,response::text FROM inventory_operations WHERE actor_id=? AND request_id=?", (row, index) -> {
            if (!row.getString("fingerprint").equals(fingerprint)) { throw new ApiFailure(409, "request_conflict"); }
            return json.readValue(row.getString("response"), Operation.class);
        }, actor, request);
        return rows.stream().findFirst();
    }
    public Balance change(Item item) {
        String filter = " WHERE product_id=? AND version=? AND version<9223372036854775807";
        String suffix = " RETURNING *,physical-reserved AS available";
        String base = "UPDATE inventory_balances SET version=version+1,updated_at=clock_timestamp(),physical=";
        var rows = switch (item.type()) {
            case "ENTRY" -> jdbc.query(base + "physical+?" + filter + " AND physical<=?" + suffix, InventoryStore::balance,
                    item.quantity(), item.productId(), item.version(), Long.MAX_VALUE - item.quantity());
            case "EXIT" -> jdbc.query(base + "physical-?" + filter + " AND physical-reserved>=?" + suffix, InventoryStore::balance,
                    item.quantity(), item.productId(), item.version(), item.quantity());
            case "ADJUST" -> jdbc.query(base + "?" + filter + " AND reserved<=?" + suffix, InventoryStore::balance,
                    item.quantity(), item.productId(), item.version(), item.quantity());
            default -> throw new ApiFailure(400, "invalid_request");
        };
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", (row, index) -> row.getTimestamp(1).toInstant()); }
    public void append(Operation operation, UUID actor, String fingerprint) {
        jdbc.update("INSERT INTO inventory_operations(id,actor_id,request_id,fingerprint,response,created_at) VALUES (?,?,?,?,?::jsonb,?)",
                operation.id(), actor, operation.requestId(), fingerprint, json.writeValueAsString(operation), Timestamp.from(operation.createdAt()));
        for (var item : operation.items()) {
            var after = item.balance();
            jdbc.update("""
                    INSERT INTO inventory_movements(id,operation_id,product_id,actor_id,type,quantity,delta,reason,
                        physical_before,physical_after,reserved_before,reserved_after,version_before,version_after,
                        balance_created_at,balance_updated_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, item.id(), operation.id(), item.productId(), actor, item.type(), item.quantity(), item.delta(), item.reason(),
                    item.physicalBefore(), after.physical(), item.reservedBefore(), after.reserved(), item.versionBefore(), after.version(),
                    Timestamp.from(after.createdAt()), Timestamp.from(after.updatedAt()), Timestamp.from(item.createdAt()));
        }
    }
    public Page<Movement> history(UUID id, int page, int size) {
        var rows = jdbc.query("SELECT * FROM inventory_movements WHERE product_id=? ORDER BY version_after,id LIMIT ? OFFSET ?", (row, index) -> {
            var balance = new Balance(id, row.getLong("physical_after"), row.getLong("reserved_after"), row.getLong("physical_after") - row.getLong("reserved_after"),
                    row.getLong("version_after"), row.getTimestamp("balance_created_at").toInstant(), row.getTimestamp("balance_updated_at").toInstant());
            return new Movement(row.getObject("id", UUID.class), row.getObject("operation_id", UUID.class), id, row.getString("type"), row.getLong("quantity"), row.getLong("delta"),
                    row.getString("reason"), row.getObject("actor_id", UUID.class), row.getLong("physical_before"), row.getLong("reserved_before"), row.getLong("version_before"),
                    balance, row.getTimestamp("created_at").toInstant());
        }, id, size, (long) page * size);
        return new Page<>(rows, jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=?", Long.class, id), page, size);
    }
}
