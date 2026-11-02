package dev.pdv.api.sales.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CheckoutModels {
    private CheckoutModels() { }
    public enum Method { CARTAO_CREDITO, CARTAO_DEBITO, PIX, BOLETO, DINHEIRO }
    public enum Scenario { APROVADO, RECUSADO, TIMEOUT }
    public record Checkout(@NotNull @PositiveOrZero Long version, @NotNull Method method, @NotNull Scenario scenario) { }
    public record Sale(UUID id, long number, UUID cartId, UUID customerId, UUID reservationId, UUID paymentId,
            String status, String paymentStatus, Method method, Scenario scenario, long version, BigDecimal total,
            List<CartModels.Line> items, Instant createdAt, Instant updatedAt) { }
    public record Execution(Sale sale, boolean replayed) { }
}
