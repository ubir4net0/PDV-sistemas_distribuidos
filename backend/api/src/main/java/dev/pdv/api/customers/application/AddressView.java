package dev.pdv.api.customers.application;

import java.time.Instant;
import java.util.UUID;

public record AddressView(UUID id, UUID customerId, String type, String street, String number, String complement,
        String neighborhood, String city, String state, String postalCode, boolean primary, long version,
        Instant createdAt, Instant updatedAt) {
    @Override
    public String toString() { return "AddressView[id=" + id + ", data=REDACTED]"; }
}
