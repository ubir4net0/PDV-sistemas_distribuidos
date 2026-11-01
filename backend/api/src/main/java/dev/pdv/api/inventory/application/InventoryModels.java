package dev.pdv.api.inventory.application;

import java.time.Instant;
import java.util.UUID;

public final class InventoryModels {
    private InventoryModels() { }
    public record Balance(UUID productId, long physical, long reserved, long available, long version,
            Instant createdAt, Instant updatedAt) { }
    public record Opening(UUID productId, String source, long physical, long reserved, long version, Instant recordedAt) { }
}
