package dev.pdv.api.purchasing.infrastructure;

import dev.pdv.api.customers.application.Page;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SupplierAudit {
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    public SupplierAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String snapshot(UUID id) { return jdbc.queryForObject("SELECT to_jsonb(s)::text FROM suppliers s WHERE id=?", String.class, id); }
    public void append(UUID id, UUID actor, String action, String before) {
        jdbc.update("INSERT INTO supplier_changes(supplier_id,actor_id,action,before_data,after_data) VALUES (?,?,?,?::jsonb,?::jsonb)", id, actor, action, before, snapshot(id));
    }
    public Page<Change> history(UUID id, int page, int size) {
        var items = jdbc.query("SELECT * FROM supplier_changes WHERE supplier_id=? ORDER BY created_at,id LIMIT ? OFFSET ?",
                (row, index) -> new Change(row.getObject("id", UUID.class), row.getObject("actor_id", UUID.class), row.getString("action"),
                        json.readTree(row.getString("before_data")), json.readTree(row.getString("after_data")), row.getTimestamp("created_at").toInstant()), id, size, (long) page * size);
        return new Page<>(items, jdbc.queryForObject("SELECT count(*) FROM supplier_changes WHERE supplier_id=?", Long.class, id), page, size);
    }
    public record Change(UUID id, UUID actorId, String action, JsonNode before, JsonNode after, Instant createdAt) {
        @Override public String toString() { return "SupplierChange[id=" + id + ", data=REDACTED]"; }
    }
}
