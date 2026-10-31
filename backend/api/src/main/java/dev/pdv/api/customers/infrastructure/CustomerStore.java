package dev.pdv.api.customers.infrastructure;

import dev.pdv.api.customers.application.CustomerInput;
import dev.pdv.api.customers.application.CustomerView;
import jakarta.persistence.EntityManager;
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
}
