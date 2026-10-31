package dev.pdv.api.purchasing.infrastructure;

import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.catalog.infrastructure.ProductEntity;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.purchasing.application.SupplierModels.View;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SupplierStore {
    private final EntityManager entities;
    public SupplierStore(EntityManager entities) { this.entities = entities; }
    public SupplierEntity require(UUID id, boolean write) {
        var result = entities.find(SupplierEntity.class, id, write ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (result == null) { throw new ApiFailure(404, "supplier_not_found"); } return result;
    }
    public SupplierEntity insert(SupplierEntity entity) { entities.persist(entity); return flush(entity); }
    public SupplierEntity flush(SupplierEntity entity) { entities.flush(); entities.refresh(entity); return entity; }
    public Page<View> list(Boolean active, int page, int size) {
        String filter = " FROM SupplierEntity s" + (active == null ? "" : " WHERE s.active=:active");
        var query = entities.createQuery("SELECT s" + filter + " ORDER BY s.legalName,s.id", SupplierEntity.class);
        var count = entities.createQuery("SELECT count(s)" + filter, Long.class);
        if (active != null) { query.setParameter("active", active); count.setParameter("active", active); }
        return new Page<>(query.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(SupplierEntity::view).toList(), count.getSingleResult(), page, size);
    }
    public Page<ProductView> products(UUID id, int page, int size) {
        var query = entities.createQuery("SELECT p FROM ProductEntity p WHERE p.supplierId=:id ORDER BY p.name,p.id", ProductEntity.class).setParameter("id", id);
        long count = entities.createQuery("SELECT count(p) FROM ProductEntity p WHERE p.supplierId=:id", Long.class).setParameter("id", id).getSingleResult();
        return new Page<>(query.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(ProductEntity::view).toList(), count, page, size);
    }
}
