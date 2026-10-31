package dev.pdv.api.customers.infrastructure;

import java.util.List;
import java.util.UUID;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.AddressView;
import dev.pdv.api.customers.application.Page;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AddressStore {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;

    public AddressStore(EntityManager entities, JdbcTemplate jdbc) { this.entities = entities; this.jdbc = jdbc; }

    public void persist(AddressEntity entity) { entities.persist(entity); }
    public void refresh(AddressEntity entity) { entities.refresh(entity); }

    public AddressEntity require(UUID id, UUID customer) {
        var entity = entities.find(AddressEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null || !entity.view().customerId().equals(customer)) {
            throw new ApiFailure(404, "address_not_found");
        }
        return entity;
    }

    public void demoteOthers(UUID customer, UUID except) {
        jdbc.update("""
                UPDATE customer_addresses SET is_primary=false, version=version+1, updated_at=clock_timestamp()
                WHERE customer_id=? AND is_primary AND id<>?
                """, customer, except);
    }

    public Page<AddressView> list(UUID customer, int page, int size) {
        var items = entities.createQuery("""
                SELECT a FROM AddressEntity a WHERE a.customerId=:customer ORDER BY a.main DESC,a.createdAt,a.id
                """, AddressEntity.class).setParameter("customer", customer).setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(AddressEntity::view).toList();
        long total = entities.createQuery("SELECT count(a) FROM AddressEntity a WHERE a.customerId=:customer", Long.class)
                .setParameter("customer", customer).getSingleResult();
        return new Page<>(items, total, page, size);
    }

    public String snapshot(UUID customer) {
        return jdbc.queryForObject("""
                SELECT jsonb_build_object('customer',to_jsonb(c),'addresses',COALESCE(
                    (SELECT jsonb_agg(to_jsonb(a) ORDER BY a.id) FROM customer_addresses a WHERE a.customer_id=c.id),
                    '[]'::jsonb))::text FROM customers c WHERE c.id=?
                """, String.class, customer);
    }

    public List<Region> regions(Boolean active, String state, String city) {
        return jdbc.query("""
                SELECT a.city,a.state,count(*) AS customers FROM customers c
                LEFT JOIN customer_addresses a ON a.customer_id=c.id AND a.is_primary
                WHERE (?::boolean IS NULL OR c.active=?) AND (?::text IS NULL OR a.state=?)
                  AND (?::text IS NULL OR lower(a.city)=lower(?))
                GROUP BY a.city,a.state ORDER BY a.state NULLS LAST,a.city NULLS LAST
                """, (row, index) -> new Region(row.getString("city"), row.getString("state"), row.getLong("customers")),
                active, active, state, state, city, city);
    }

    public record Region(String city, String state, long customers) { }
}
