package dev.pdv.api.sales.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe projection; internal audit snapshots are never serialized. */
public record SaleTimeline(UUID saleId, String traceId, List<Event> events, List<Delivery> deliveries) {
    public record Event(UUID id, long version, String kind, String saleStatus, String paymentStatus, Instant occurredAt) { }
    public record Delivery(UUID eventId, String type, String status, int attempts, String error, Instant updatedAt) { }
}
