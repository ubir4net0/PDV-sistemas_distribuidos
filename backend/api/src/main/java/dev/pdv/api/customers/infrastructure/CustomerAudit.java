package dev.pdv.api.customers.infrastructure;

import java.time.Instant;
import java.util.UUID;
import dev.pdv.api.customers.application.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CustomerAudit {
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    public CustomerAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (row, index) -> row.getTimestamp(1).toInstant());
    }

    public String snapshot(UUID customer) {
        return jdbc.queryForObject("SELECT to_jsonb(c)::text FROM customers c WHERE id=?", String.class, customer);
    }

    public void append(UUID customer, UUID actor, String action, String before, String after) {
        jdbc.update("""
                INSERT INTO customer_changes(customer_id,actor_id,action,before_data,after_data) VALUES (?,?,?,?::jsonb,?::jsonb)
                """, customer, actor, action, before, after);
    }

    public Page<Change> history(UUID id, int page, int size) {
        var items = jdbc.query("""
                SELECT * FROM customer_changes WHERE customer_id=? ORDER BY created_at,id LIMIT ? OFFSET ?
                """, (row, index) -> new Change(row.getObject("id", UUID.class), row.getObject("actor_id", UUID.class),
                row.getString("action"), json.readTree(row.getString("before_data")), json.readTree(row.getString("after_data")),
                row.getTimestamp("created_at").toInstant()), id, size, (long) page * size);
        long total = jdbc.queryForObject("SELECT count(*) FROM customer_changes WHERE customer_id=?", Long.class, id);
        return new Page<>(items, total, page, size);
    }

    public record Change(UUID id, UUID actorId, String action, JsonNode before, JsonNode after, Instant createdAt) {
        @Override
        public String toString() { return "CustomerChange[id=" + id + ", data=REDACTED]"; }
    }
}
