package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.ReservationModels.Item;
import dev.pdv.api.inventory.application.ReservationModels.Reservation;
import dev.pdv.api.inventory.application.ReservationModels.ReservedItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ReservationStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public ReservationStore(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }
    public Optional<Reservation> replay(UUID actor, UUID request, String fingerprint) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (r, i) -> 0, "reservation:" + actor + ":" + request);
        return jdbc.query("SELECT fingerprint,response::text FROM inventory_reservation_operations WHERE actor_id=? AND request_id=?", (r, i) -> {
            if (!fingerprint.equals(r.getString(1))) { throw new ApiFailure(409, "request_conflict"); }
            return json.readValue(r.getString(2), Reservation.class);
        }, actor, request).stream().findFirst();
    }
    public void saveResponse(UUID actor, UUID request, String fingerprint, Reservation result) {
        jdbc.update("INSERT INTO inventory_reservation_operations(actor_id,request_id,fingerprint,reservation_id,response) VALUES (?,?,?,?,?::jsonb)",
                actor, request, fingerprint, result.id(), json.writeValueAsString(result));
    }
    public void activeProducts(List<UUID> ids) {
        for (var id : ids) {
            var states = jdbc.query("SELECT status FROM products WHERE id=? FOR SHARE", (r, i) -> r.getString(1), id);
            if (states.isEmpty()) { throw new ApiFailure(404, "product_not_found"); }
            if (!"ATIVO".equals(states.getFirst())) { throw new ApiFailure(409, "product_inactive"); }
        }
    }
    public void reserve(Item item) {
        int changed = jdbc.update("""
                UPDATE inventory_balances SET reserved=reserved+?,version=version+1,updated_at=clock_timestamp()
                WHERE product_id=? AND version=? AND version<9223372036854775807 AND physical-reserved>=?
                """, item.quantity(), item.productId(), item.version(), item.quantity());
        if (changed != 1) { throw new ApiFailure(409, "reservation_conflict"); }
    }
    public void create(UUID id, UUID actor, List<Item> items, int ttl) {
        jdbc.update("""
                INSERT INTO inventory_reservations(id,actor_id,expires_at,created_at,updated_at)
                SELECT ?,?,t.now + (? * interval '1 second'),t.now,t.now FROM (SELECT clock_timestamp() AS now) t
                """, id, actor, ttl);
        for (var item : items) { jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,?)", id, item.productId(), item.quantity()); }
        event(id, "CREATED");
    }
    public Reservation own(UUID id, UUID actor, boolean lock) {
        var rows = jdbc.query("SELECT * FROM inventory_reservations WHERE id=? AND actor_id=?" + (lock ? " FOR UPDATE" : ""), (r, i) ->
                new Reservation(r.getObject("id", UUID.class), r.getString("status"), r.getBoolean("payment_protected"), r.getTimestamp("expires_at").toInstant(),
                    r.getLong("version"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(), List.of()), id, actor);
        if (rows.isEmpty()) { throw new ApiFailure(404, "reservation_not_found"); }
        var r = rows.getFirst();
        var items = jdbc.query("SELECT product_id,quantity FROM inventory_reservation_items WHERE reservation_id=? ORDER BY product_id", (row, i) ->
                new ReservedItem(row.getObject(1, UUID.class), row.getLong(2)), id);
        return new Reservation(r.id(), r.status(), r.paymentProtected(), r.expiresAt(), r.version(), r.createdAt(), r.updatedAt(), items);
    }
    public Page<Reservation> list(UUID actor, int page, int size) {
        var ids = jdbc.query("SELECT id FROM inventory_reservations WHERE actor_id=? ORDER BY created_at,id LIMIT ? OFFSET ?", (r, i) -> r.getObject(1, UUID.class), actor, size, (long) page * size);
        return new Page<>(ids.stream().map(id -> own(id, actor, false)).toList(), jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE actor_id=?", Long.class, actor), page, size);
    }
    public Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", (r, i) -> r.getTimestamp(1).toInstant()); }
    public void renew(Reservation reservation, int ttl) {
        int changed = jdbc.update("""
                UPDATE inventory_reservations SET expires_at=clock_timestamp()+(? * interval '1 second'),version=version+1,updated_at=clock_timestamp()
                WHERE id=? AND status='ACTIVE' AND NOT payment_protected AND expires_at>clock_timestamp() AND version=? AND version<9223372036854775807
                """, ttl, reservation.id(), reservation.version());
        if (changed != 1) { throw new ApiFailure(409, "reservation_not_renewable"); }
        event(reservation.id(), "RENEWED");
    }
    private void event(UUID id, String kind) { jdbc.query("SELECT record_inventory_reservation_event(?,?)", (r, i) -> 0, id, kind); }
}
