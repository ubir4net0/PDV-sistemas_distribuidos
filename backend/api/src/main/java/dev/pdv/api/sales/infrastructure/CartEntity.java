package dev.pdv.api.sales.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class CartEntity {
    @Id private UUID id;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "customer_id", nullable = false, updatable = false) private UUID customerId;
    @Version private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected CartEntity() { }
    public CartEntity(UUID id, UUID actor, UUID customer, Instant now) { this.id = id; this.actorId = actor; this.customerId = customer; this.createdAt = now; this.updatedAt = now; }
    public UUID id() { return id; }
    public UUID customerId() { return customerId; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public void touch(Instant now) { updatedAt = now; }
}
