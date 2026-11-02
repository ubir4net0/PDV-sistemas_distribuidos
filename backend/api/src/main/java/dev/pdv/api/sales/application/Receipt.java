package dev.pdv.api.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Receipt(UUID id, UUID saleId, long number, UUID customerId, UUID paymentId,
        CheckoutModels.Method method, BigDecimal total, Instant issuedAt, List<CartModels.Line> items) { }
