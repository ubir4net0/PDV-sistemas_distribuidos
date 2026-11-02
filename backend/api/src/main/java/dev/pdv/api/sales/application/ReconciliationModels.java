package dev.pdv.api.sales.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ReconciliationModels {
    private ReconciliationModels() { }
    public enum Outcome { APROVADO, RECUSADO }
    public record Resolve(@NotNull UUID requestId, @NotNull Outcome outcome, @NotBlank @Size(max = 500) String reason) { }
    public record Reconcile(@NotNull UUID requestId) { }
    public record Resolution(UUID paymentId, Outcome outcome, Instant recordedAt) { }
    public record Reconciliation(UUID saleId, String paymentStatus, String saleStatus, String reservationStatus, boolean pending, UUID receiptId) { }
    public record Execution<T>(T response, int status, boolean replayed) { }
}
