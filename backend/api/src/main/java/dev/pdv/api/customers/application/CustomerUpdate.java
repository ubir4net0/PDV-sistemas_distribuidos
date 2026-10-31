package dev.pdv.api.customers.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CustomerUpdate(@NotNull @Valid CustomerInput customer, @NotNull @PositiveOrZero Long version) {
    @Override
    public String toString() { return "CustomerUpdate[REDACTED]"; }
}
