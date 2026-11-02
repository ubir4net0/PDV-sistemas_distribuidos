package dev.pdv.api.sales.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CartModels {
    private CartModels() { }
    public record Create(@NotNull UUID requestId, @NotNull UUID customerId) { }
    public record SetItem(@NotNull @Positive Long quantity, @NotNull @PositiveOrZero Long version) { }
    public record Line(UUID productId, long quantity, BigDecimal unitPrice, BigDecimal subtotal) { }
    public record Cart(UUID id, UUID customerId, long version, BigDecimal total, List<Line> items, Instant createdAt, Instant updatedAt) { }
    public record Execution(Cart cart, boolean replayed) { }
}
