package dev.pdv.api.sales.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.sales.application.CartModels;
import dev.pdv.api.sales.application.Receipt;
import dev.pdv.api.sales.application.CheckoutModels.Checkout;
import dev.pdv.api.sales.application.CheckoutModels.Method;
import dev.pdv.api.sales.application.CheckoutModels.Sale;
import dev.pdv.api.sales.application.CheckoutModels.Scenario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CheckoutStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public CheckoutStore(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }
    public Optional<Sale> replay(UUID actor, String key, String fingerprint) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (r, i) -> 0, "checkout:" + actor + ":" + key);
        return jdbc.query("SELECT fingerprint,response::text FROM checkout_operations WHERE actor_id=? AND idempotency_key=?", (r, i) -> {
            if (!fingerprint.equals(r.getString(1))) { throw new ApiFailure(409, "request_conflict"); }
            return json.readValue(r.getString(2), Sale.class);
        }, actor, key).stream().findFirst();
    }
    public void reserve(CartModels.Line item) {
        int changed = jdbc.update("""
                UPDATE inventory_balances SET reserved=reserved+?,version=version+1,updated_at=clock_timestamp()
                WHERE product_id=? AND version<9223372036854775807 AND physical-reserved>=?
                """, item.quantity(), item.productId(), item.quantity());
        if (changed != 1) { throw new ApiFailure(409, "reservation_conflict"); }
    }
    public void insert(UUID sale, UUID reservation, UUID payment, UUID actor, CartModels.Cart cart, Checkout input) {
        jdbc.update("INSERT INTO sales(id,cart_id,actor_id,customer_id,reservation_id,total) VALUES (?,?,?,?,?,?)", sale, cart.id(), actor, cart.customerId(), reservation, cart.total());
        for (var line : cart.items()) { jdbc.update("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price) VALUES (?,?,?,?)", sale, line.productId(), line.quantity(), line.unitPrice()); }
        jdbc.update("INSERT INTO payments(id,sale_id,method,scenario,amount) VALUES (?,?,?,?,?)", payment, sale, input.method().name(), input.scenario().name(), cart.total());
        jdbc.update("""
                INSERT INTO payment_intents(payment_id,payload)
                SELECT id,jsonb_build_object('version',1,'paymentId',id,'saleId',sale_id,'method',method,'scenario',scenario,'amount',amount) FROM payments WHERE id=?
                """, payment);
    }
    public Sale own(UUID id, UUID actor) {
        var rows = jdbc.query("""
                SELECT s.*,p.id AS payment_id,p.status AS payment_status,p.method,p.scenario FROM sales s JOIN payments p ON p.sale_id=s.id WHERE s.id=? AND s.actor_id=?
                """, (r, i) -> new Sale(r.getObject("id", UUID.class), r.getLong("number"), r.getObject("cart_id", UUID.class), r.getObject("customer_id", UUID.class),
                    r.getObject("reservation_id", UUID.class), r.getObject("payment_id", UUID.class), r.getString("status"), r.getString("payment_status"),
                    Method.valueOf(r.getString("method")), Scenario.valueOf(r.getString("scenario")), r.getLong("version"), r.getBigDecimal("total"), java.util.List.of(),
                    r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant()), id, actor);
        if (rows.isEmpty()) { throw new ApiFailure(404, "sale_not_found"); }
        var s = rows.getFirst(); var lines = jdbc.query("SELECT product_id,quantity,unit_price,subtotal FROM sale_items WHERE sale_id=? ORDER BY product_id", (r, i) ->
                new CartModels.Line(r.getObject(1, UUID.class), r.getLong(2), r.getBigDecimal(3), r.getBigDecimal(4)), id);
        return new Sale(s.id(), s.number(), s.cartId(), s.customerId(), s.reservationId(), s.paymentId(), s.status(), s.paymentStatus(), s.method(), s.scenario(), s.version(), s.total(), lines, s.createdAt(), s.updatedAt());
    }
    public void record(UUID actor, String key, String fingerprint, Sale result) {
        String snapshot = json.writeValueAsString(result);
        jdbc.update("INSERT INTO sale_events(sale_id,version,kind,snapshot) VALUES (?,0,'CHECKOUT',?::jsonb)", result.id(), snapshot);
        jdbc.update("INSERT INTO checkout_operations(actor_id,idempotency_key,sale_id,fingerprint,http_status,response) VALUES (?,?,?,?,201,?::jsonb)", actor, key, result.id(), fingerprint, snapshot);
    }
    public Receipt receipt(UUID id, UUID actor) {
        return jdbc.query("SELECT r.snapshot::text FROM receipts r JOIN sales s ON s.id=r.sale_id WHERE s.id=? AND s.actor_id=? AND s.status='CONCLUIDA'",
                (r, i) -> json.readValue(r.getString(1), Receipt.class), id, actor).stream().findFirst().orElseThrow(() -> new ApiFailure(404, "receipt_not_found"));
    }
}
