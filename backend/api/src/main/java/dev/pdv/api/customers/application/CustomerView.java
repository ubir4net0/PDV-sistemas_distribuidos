package dev.pdv.api.customers.application;

import java.time.Instant;
import java.util.UUID;

public record CustomerView(UUID id, String personType, String document, String name, String phone,
        String email, String locality, boolean active, long version, Instant createdAt, Instant updatedAt) {
    @Override
    public String toString() { return "CustomerView[id=" + id + ", data=REDACTED]"; }
}
