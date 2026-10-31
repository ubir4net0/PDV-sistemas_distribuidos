package dev.pdv.api.customers.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AddressUpdate(@NotNull @Valid AddressInput address, @NotNull @PositiveOrZero Long version) {
    @Override
    public String toString() { return "AddressUpdate[REDACTED]"; }
}
