package dev.pdv.api.customers.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CustomerStatus(@NotNull Boolean active, @NotNull @PositiveOrZero Long version) { }
