package dev.pdv.api.catalog.infrastructure;

import dev.pdv.api.customers.application.Page;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CatalogAudit {
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    public CatalogAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", (row, index) -> row.getTimestamp(1).toInstant()); }
    public String snapshot(UUID id, boolean product) {
        return jdbc.queryForObject("SELECT to_jsonb(c)::text FROM " + (product ? "products" : "categories") + " c WHERE id=?", String.class, id);
    }
    public void append(UUID id, boolean product, UUID actor, String action, String before) {
        jdbc.update("INSERT INTO catalog_changes(" + (product ? "product_id" : "category_id")
                + ",actor_id,action,before_data,after_data) VALUES (?,?,?,?::jsonb,?::jsonb)", id, actor, action, before, snapshot(id, product));
    }
    public Page<Change> history(UUID id, boolean product, int page, int size) {
        String filter = " FROM catalog_changes WHERE " + (product ? "product_id" : "category_id") + "=?";
        var items = jdbc.query("SELECT *" + filter + " ORDER BY created_at,id LIMIT ? OFFSET ?",
                (row, index) -> new Change(row.getObject("id", UUID.class), row.getObject("actor_id", UUID.class), row.getString("action"),
                        json.readTree(row.getString("before_data")), json.readTree(row.getString("after_data")), row.getTimestamp("created_at").toInstant()),
                id, size, (long) page * size);
        return new Page<>(items, jdbc.queryForObject("SELECT count(*)" + filter, Long.class, id), page, size);
    }
    public record Change(UUID id, UUID actorId, String action, JsonNode before, JsonNode after, Instant createdAt) { }
}
