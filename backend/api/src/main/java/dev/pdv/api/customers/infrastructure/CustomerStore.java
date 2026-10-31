package dev.pdv.api.customers.infrastructure;

import dev.pdv.api.customers.application.CustomerInput;
import dev.pdv.api.customers.application.CustomerView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerStore {
    private final EntityManager entities;

    public CustomerStore(EntityManager entities) { this.entities = entities; }

    public CustomerView create(CustomerInput input, String document) {
        var entity = new CustomerEntity(input, document);
        entities.persist(entity);
        entities.flush();
        entities.refresh(entity);
        return entity.view();
    }

    public CustomerEntity require(UUID id, boolean write) {
        var entity = entities.find(CustomerEntity.class, id, write ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (entity == null) {
            throw new ApiFailure(404, "customer_not_found");
        }
        return entity;
    }

    public CustomerView flush(CustomerEntity entity) {
        entities.flush();
        entities.refresh(entity);
        return entity.view();
    }

    public Page<CustomerView> list(String name, String document, Boolean active, int page, int size) {
        String filter = " FROM CustomerEntity c WHERE (:active IS NULL OR c.active=:active)"
                + " AND (:document IS NULL OR c.document=:document) AND LOCATE(LOWER(:name),LOWER(c.name)) > 0";
        var query = entities.createQuery("SELECT c" + filter + " ORDER BY c.name,c.id", CustomerEntity.class);
        var count = entities.createQuery("SELECT count(c)" + filter, Long.class);
        for (var typed : java.util.List.of(query, count)) {
            typed.setParameter("name", name).setParameter("document", document).setParameter("active", active);
        }
        var items = query.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(CustomerEntity::view).toList();
        return new Page<>(items, count.getSingleResult(), page, size);
    }
}
