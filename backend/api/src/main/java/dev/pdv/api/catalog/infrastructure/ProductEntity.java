package dev.pdv.api.catalog.infrastructure;

import dev.pdv.api.catalog.application.CatalogModels.ProductInput;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {
    @Id private UUID id;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(nullable = false, length = 64) private String sku;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 2000) private String description;
    @Column(name = "sale_price", precision = 19, scale = 2, nullable = false) private BigDecimal salePrice;
    @Column(precision = 19, scale = 2, nullable = false) private BigDecimal cost;
    @Column(nullable = false, length = 32) private String unit;
    @Column(name = "minimum_stock", nullable = false) private long minimumStock;
    @Column(nullable = false, length = 20) private String status;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false) private Instant updatedAt;

    protected ProductEntity() { }
    public ProductEntity(ProductInput input, String canonicalSku) {
        id = UUID.randomUUID(); status = "CADASTRADO"; change(input, canonicalSku, null);
    }
    public ProductView view() {
        return new ProductView(id, categoryId, sku, name, description, salePrice, cost, unit, minimumStock,
                status, version, createdAt, updatedAt);
    }
    public void change(ProductInput input, String canonicalSku, Instant now) {
        categoryId = input.categoryId(); sku = canonicalSku; name = input.name().strip(); description = input.description();
        salePrice = input.salePrice(); cost = input.cost(); unit = input.unit().strip(); minimumStock = input.minimumStock(); updatedAt = now;
    }
    public void status(String value, Instant now) { status = value; updatedAt = now; }
}
