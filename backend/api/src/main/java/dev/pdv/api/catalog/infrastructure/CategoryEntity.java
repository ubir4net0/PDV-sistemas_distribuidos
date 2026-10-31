package dev.pdv.api.catalog.infrastructure;

import dev.pdv.api.catalog.application.CatalogModels.CategoryInput;
import dev.pdv.api.catalog.application.CatalogModels.CategoryView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 2000) private String description;
    private boolean active;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false) private Instant updatedAt;

    protected CategoryEntity() { }
    public CategoryEntity(CategoryInput input) { id = UUID.randomUUID(); active = true; change(input, null); }
    public CategoryView view() { return new CategoryView(id, name, description, active, version, createdAt, updatedAt); }
    public void change(CategoryInput input, Instant now) {
        name = input.name().strip(); description = input.description(); updatedAt = now;
    }
    public void active(boolean value, Instant now) { active = value; updatedAt = now; }
}
