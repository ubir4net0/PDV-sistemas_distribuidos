package dev.pdv.api.sales.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.sales.application.CartModels.Cart;
import dev.pdv.api.sales.application.CartModels.Line;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CartStore {
    @PersistenceContext private EntityManager entities;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public CartStore(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }
    public Optional<Cart> replay(UUID actor, UUID request, String fingerprint) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (r, i) -> 0, "cart.create:" + actor + ":" + request);
        return jdbc.query("SELECT fingerprint,response::text FROM cart_operations WHERE actor_id=? AND request_id=?", (r, i) -> {
            if (!fingerprint.equals(r.getString(1))) { throw new ApiFailure(409, "request_conflict"); }
            return json.readValue(r.getString(2), Cart.class);
        }, actor, request).stream().findFirst();
    }
    public void response(UUID actor, UUID request, String fingerprint, Cart cart) {
        jdbc.update("INSERT INTO cart_operations(actor_id,request_id,cart_id,fingerprint,response) VALUES (?,?,?,?,?::jsonb)", actor, request, cart.id(), fingerprint, json.writeValueAsString(cart));
    }
    public void activeCustomer(UUID id) {
        var rows = jdbc.query("SELECT active FROM customers WHERE id=? FOR SHARE", (r, i) -> r.getBoolean(1), id);
        if (rows.isEmpty()) { throw new ApiFailure(404, "customer_not_found"); }
        if (!rows.getFirst()) { throw new ApiFailure(409, "customer_inactive"); }
    }
    public void editable(UUID id) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sales WHERE cart_id=?)", Boolean.class, id))) { throw new ApiFailure(409, "cart_checked_out"); }
    }
    public BigDecimal activePrice(UUID id) {
        return jdbc.query("SELECT sale_price,status FROM products WHERE id=? FOR SHARE", (r, i) -> {
            if (!"ATIVO".equals(r.getString("status"))) { throw new ApiFailure(409, "product_inactive"); }
            return r.getBigDecimal("sale_price");
        }, id).stream().findFirst().orElseThrow(() -> new ApiFailure(404, "product_not_found"));
    }
    public CartEntity own(UUID id, UUID actor, boolean lock) {
        var query = entities.createQuery("SELECT c FROM CartEntity c WHERE c.id=:id AND c.actorId=:actor", CartEntity.class).setParameter("id", id).setParameter("actor", actor);
        if (lock) { query.setLockMode(LockModeType.PESSIMISTIC_WRITE); }
        return query.getResultList().stream().findFirst().orElseThrow(() -> new ApiFailure(404, "cart_not_found"));
    }
    public Optional<CartItemEntity> item(UUID cart, UUID product) {
        return entities.createQuery("SELECT i FROM CartItemEntity i WHERE i.cartId=:cart AND i.productId=:product", CartItemEntity.class)
                .setParameter("cart", cart).setParameter("product", product).getResultList().stream().findFirst();
    }
    public void add(Object entity) { entities.persist(entity); }
    public void remove(CartItemEntity item) { entities.remove(item); }
    public void flush() { entities.flush(); }
    public Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", (r, i) -> r.getTimestamp(1).toInstant()); }
    public Cart view(CartEntity entity) {
        var lines = entities.createQuery("SELECT i FROM CartItemEntity i WHERE i.cartId=:cart ORDER BY i.productId", CartItemEntity.class).setParameter("cart", entity.id()).getResultList()
                .stream().map(i -> new Line(i.productId(), i.quantity(), i.unitPrice(), i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))).toList();
        var total = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new Cart(entity.id(), entity.customerId(), entity.version(), total, lines, entity.createdAt(), entity.updatedAt());
    }
    public Page<Cart> list(UUID actor, int page, int size) {
        var rows = entities.createQuery("SELECT c FROM CartEntity c WHERE c.actorId=:actor ORDER BY c.createdAt,c.id", CartEntity.class).setParameter("actor", actor)
                .setFirstResult(page * size).setMaxResults(size).getResultList();
        long total = entities.createQuery("SELECT count(c) FROM CartEntity c WHERE c.actorId=:actor", Long.class).setParameter("actor", actor).getSingleResult();
        return new Page<>(rows.stream().map(this::view).toList(), total, page, size);
    }
    public void audit(UUID actor, String kind, Cart before, Cart after) {
        jdbc.update("INSERT INTO cart_changes(cart_id,actor_id,version,kind,before_data,after_data) VALUES (?,?,?,?,?::jsonb,?::jsonb)",
                after.id(), actor, after.version(), kind, before == null ? "{}" : json.writeValueAsString(before), json.writeValueAsString(after));
    }
}
