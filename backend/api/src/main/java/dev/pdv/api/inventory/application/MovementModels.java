package dev.pdv.api.inventory.application;

import dev.pdv.api.inventory.application.InventoryModels.Balance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MovementModels {
    private MovementModels() { }
    public record Item(@NotNull UUID productId, @NotNull @Pattern(regexp = "ENTRY|EXIT|ADJUST") String type,
            @NotNull @PositiveOrZero Long quantity, @NotBlank @Size(max = 500) String reason, @NotNull @PositiveOrZero Long version) {
        @Override public String toString() { return "MovementItem[REDACTED]"; }
    }
    public record Single(@NotNull UUID requestId, @NotNull @Pattern(regexp = "ENTRY|EXIT|ADJUST") String type,
            @NotNull @PositiveOrZero Long quantity, @NotBlank @Size(max = 500) String reason, @NotNull @PositiveOrZero Long version) {
        public Item item(UUID id) { return new Item(id, type, quantity, reason, version); }
        @Override public String toString() { return "MovementInput[REDACTED]"; }
    }
    public record Batch(@NotNull UUID requestId, @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items) { }
    public record Movement(UUID id, UUID operationId, UUID productId, String type, long quantity, long delta, String reason,
            UUID actorId, long physicalBefore, long reservedBefore, long versionBefore, Balance balance, Instant createdAt) {
        @Override public String toString() { return "InventoryMovement[id=" + id + ", data=REDACTED]"; }
    }
    public record Operation(UUID id, UUID requestId, List<Movement> items, Instant createdAt) { }
    public record Execution(Operation operation, boolean replayed) { }
}
