package dev.pdv.api.inventory.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReservationModels {
    private ReservationModels() { }
    public record Item(@NotNull UUID productId, @NotNull @Positive Long quantity, @NotNull @PositiveOrZero Long version) { }
    public record Create(@NotNull UUID requestId, @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items) { }
    public record Renew(@NotNull UUID requestId, @NotNull @PositiveOrZero Long version) { }
    public record ReservedItem(UUID productId, long quantity) { }
    public record Reservation(UUID id, String status, boolean paymentProtected, Instant expiresAt, long version,
            Instant createdAt, Instant updatedAt, List<ReservedItem> items) { }
    public record Execution(Reservation reservation, boolean replayed) { }
}
