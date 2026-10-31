package dev.pdv.api.catalog.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogStore {
    private final EntityManager entities;
    public CatalogStore(EntityManager entities) { this.entities = entities; }
    public CategoryEntity category(UUID id, boolean write) { return require(CategoryEntity.class, id, write, "category_not_found"); }
    public ProductEntity product(UUID id, boolean write) { return require(ProductEntity.class, id, write, "product_not_found"); }
    private <T> T require(Class<T> type, UUID id, boolean write, String code) {
        var result = entities.find(type, id, write ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (result == null) { throw new ApiFailure(404, code); }
        return result;
    }
    public <T> T insert(T entity) { entities.persist(entity); return flush(entity); }
    public <T> T flush(T entity) { entities.flush(); entities.refresh(entity); return entity; }
}
