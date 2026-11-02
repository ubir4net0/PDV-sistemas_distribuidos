package dev.pdv.api.sales.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItemEntity {
    @Id private UUID id;
    @Column(name = "cart_id", nullable = false, updatable = false) private UUID cartId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(nullable = false) private long quantity;
    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal unitPrice;
    protected CartItemEntity() { }
    public CartItemEntity(UUID cart, UUID product, long quantity, BigDecimal price) { this.id = UUID.randomUUID(); this.cartId = cart; this.productId = product; this.quantity = quantity; this.unitPrice = price; }
    public UUID productId() { return productId; }
    public long quantity() { return quantity; }
    public BigDecimal unitPrice() { return unitPrice; }
    public void quantity(long value) { quantity = value; }
}
