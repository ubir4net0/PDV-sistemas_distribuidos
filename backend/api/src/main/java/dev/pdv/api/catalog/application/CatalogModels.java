package dev.pdv.api.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class CatalogModels {
    private CatalogModels() { }

    public record CategoryInput(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) { }
    public record CategoryUpdate(@NotNull @Valid CategoryInput category, @NotNull @PositiveOrZero Long version) { }
    public record ActiveUpdate(@NotNull Boolean active, @NotNull @PositiveOrZero Long version) { }
    public record StateUpdate(@NotNull @Pattern(regexp = "CADASTRADO|EM_REVISAO|ATIVO|INATIVO") String status,
            @NotNull @PositiveOrZero Long version) { }
    public record ProductInput(@NotNull UUID categoryId, @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
            @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal salePrice,
            @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal cost,
            @NotBlank @Size(max = 32) String unit, @NotNull @PositiveOrZero Long minimumStock) { }
    public record ProductUpdate(@NotNull @Valid ProductInput product, @NotNull @PositiveOrZero Long version) { }
    public record CategoryView(UUID id, String name, String description, boolean active, long version,
            Instant createdAt, Instant updatedAt) { }
    public record ProductView(UUID id, UUID categoryId, String sku, String name, String description,
            BigDecimal salePrice, BigDecimal cost, String unit, long minimumStock, String status, long version, UUID supplierId,
            Instant createdAt, Instant updatedAt) { }
}
