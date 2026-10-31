package dev.pdv.api.catalog.application;

import dev.pdv.api.catalog.application.CatalogModels.CategoryView;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.catalog.infrastructure.CategoryEntity;
import dev.pdv.api.catalog.infrastructure.ProductEntity;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('catalog.read')")
public class CatalogQueryService {
    private final EntityManager entities;
    public CatalogQueryService(EntityManager entities) { this.entities = entities; }
    public Page<ProductView> products(String q, UUID categoryId, String status, int page, int size) {
        Page.validate(page, size);
        if ((q != null && q.length() > 200) || (status != null && !List.of("CADASTRADO", "EM_REVISAO", "ATIVO", "INATIVO").contains(status))) {
            throw new ApiFailure(400, "invalid_request");
        }
        StringBuilder filter = new StringBuilder(" FROM ProductEntity p WHERE 1=1");
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) {
            filter.append(" AND (LOWER(p.name) LIKE :name ESCAPE '!' OR p.sku LIKE :sku ESCAPE '!')");
            parameters.put("name", escape(q.strip().toLowerCase(Locale.ROOT)) + "%");
            parameters.put("sku", escape(q.strip().toUpperCase(Locale.ROOT)) + "%");
        }
        if (categoryId != null) { filter.append(" AND p.categoryId=:category"); parameters.put("category", categoryId); }
        if (status != null) { filter.append(" AND p.status=:status"); parameters.put("status", status); }
        var query = entities.createQuery("SELECT p" + filter + " ORDER BY p.name,p.id", ProductEntity.class);
        var count = entities.createQuery("SELECT count(p)" + filter, Long.class);
        parameters.forEach((key, value) -> { query.setParameter(key, value); count.setParameter(key, value); });
        return new Page<>(query.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(ProductEntity::view).toList(),
                count.getSingleResult(), page, size);
    }
    public Page<CategoryView> categories(Boolean active, int page, int size) {
        Page.validate(page, size);
        String filter = " FROM CategoryEntity c" + (active == null ? "" : " WHERE c.active=:active");
        var query = entities.createQuery("SELECT c" + filter + " ORDER BY c.name,c.id", CategoryEntity.class);
        var count = entities.createQuery("SELECT count(c)" + filter, Long.class);
        if (active != null) { query.setParameter("active", active); count.setParameter("active", active); }
        return new Page<>(query.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(CategoryEntity::view).toList(),
                count.getSingleResult(), page, size);
    }
    private static String escape(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
}
