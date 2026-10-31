package dev.pdv.api.purchasing.application;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class SupplierModels {
    private SupplierModels() { }
    public record Input(@NotBlank @Size(max = 18) String cnpj, @NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) String tradeName, @Size(max = 200) String contact, @Size(max = 32) String phone,
            @Email @Size(max = 320) String email, @Size(max = 1000) String address) {
        @Override public String toString() { return "SupplierInput[REDACTED]"; }
    }
    public record Update(@NotNull @Valid Input supplier, @NotNull @PositiveOrZero Long version) { }
    public record Link(UUID supplierId, @NotNull @PositiveOrZero Long version) { }
    public record View(UUID id, String cnpj, String legalName, String tradeName, String contact, String phone, String email,
            String address, boolean active, long version, Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "SupplierView[id=" + id + ", data=REDACTED]"; }
    }
}
