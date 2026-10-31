package dev.pdv.api.catalog.application;

import dev.pdv.api.catalog.application.CatalogModels.ActiveUpdate;
import dev.pdv.api.catalog.application.CatalogModels.CategoryInput;
import dev.pdv.api.catalog.application.CatalogModels.CategoryUpdate;
import dev.pdv.api.catalog.application.CatalogModels.CategoryView;
import dev.pdv.api.catalog.application.CatalogModels.ProductInput;
import dev.pdv.api.catalog.application.CatalogModels.ProductUpdate;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.catalog.application.CatalogModels.StateUpdate;
import dev.pdv.api.catalog.infrastructure.CatalogAudit;
import dev.pdv.api.catalog.infrastructure.CatalogStore;
import dev.pdv.api.catalog.infrastructure.CategoryEntity;
import dev.pdv.api.catalog.infrastructure.ProductEntity;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('catalog.read')")
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogStore store;
    private final CatalogAudit audit;
    public CatalogService(CatalogStore store, CatalogAudit audit) { this.store = store; this.audit = audit; }
    public CategoryView category(UUID id) { return store.category(id, false).view(); }
    public ProductView product(UUID id) { return store.product(id, false).view(); }
    public Page<CatalogAudit.Change> history(UUID id, boolean product, int page, int size) {
        Page.validate(page, size);
        if (product) { store.product(id, false); } else { store.category(id, false); }
        return audit.history(id, product, page, size);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog.write')")
    public CategoryView createCategory(CategoryInput input, UUID actor) {
        var result = store.insert(new CategoryEntity(input)).view();
        audit.append(result.id(), false, actor, "CREATED", "{}"); return result;
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog.write')")
    public CategoryView updateCategory(UUID id, CategoryUpdate input, UUID actor) {
        var entity = store.category(id, true); version(entity.view().version(), input.version());
        String before = audit.snapshot(id, false); entity.change(input.category(), audit.now());
        var result = store.flush(entity).view(); audit.append(id, false, actor, "UPDATED", before); return result;
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog.write')")
    public CategoryView categoryStatus(UUID id, ActiveUpdate input, UUID actor) {
        var entity = store.category(id, true); version(entity.view().version(), input.version());
        if (entity.view().active() == input.active()) { return entity.view(); }
        String before = audit.snapshot(id, false); entity.active(input.active(), audit.now());
        var result = store.flush(entity).view(); audit.append(id, false, actor, "STATUS_CHANGED", before); return result;
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog.write')")
    public ProductView createProduct(ProductInput input, UUID actor) {
        if (!store.category(input.categoryId(), true).view().active()) { throw new ApiFailure(409, "category_inactive"); }
        try {
            var result = store.insert(new ProductEntity(input, sku(input.sku()))).view();
            audit.append(result.id(), true, actor, "CREATED", "{}"); return result;
        } catch (DataIntegrityViolationException exception) { throw conflict(exception); }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog.write')")
    public ProductView updateProduct(UUID id, ProductUpdate input, UUID actor) {
        var category = store.category(input.product().categoryId(), true).view();
        var entity = store.product(id, true); version(entity.view().version(), input.version());
        if (!category.active() && !entity.view().categoryId().equals(category.id())) { throw new ApiFailure(409, "category_inactive"); }
        String before = audit.snapshot(id, true); entity.change(input.product(), sku(input.product().sku()), audit.now());
        try {
            var result = store.flush(entity).view(); audit.append(id, true, actor, "UPDATED", before); return result;
        } catch (DataIntegrityViolationException exception) { throw conflict(exception); }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('catalog.write')")
    public ProductView productStatus(UUID id, StateUpdate input, UUID actor) {
        var entity = store.product(id, true); var current = entity.view(); version(current.version(), input.version());
        if (current.status().equals(input.status())) { return current; }
        boolean allowed = switch (current.status()) {
            case "CADASTRADO" -> "EM_REVISAO".equals(input.status());
            case "EM_REVISAO", "INATIVO" -> "ATIVO".equals(input.status());
            case "ATIVO" -> "INATIVO".equals(input.status());
            default -> false;
        };
        if (!allowed) { throw new ApiFailure(409, "invalid_transition"); }
        String before = audit.snapshot(id, true); entity.status(input.status(), audit.now());
        var result = store.flush(entity).view(); audit.append(id, true, actor, "STATUS_CHANGED", before); return result;
    }
    private static void version(long actual, long expected) {
        if (actual != expected) { throw new ApiFailure(409, "version_conflict"); }
    }
    private static String sku(String raw) {
        String value = raw.strip();
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) { throw new ApiFailure(400, "invalid_request"); }
        return value.toUpperCase(Locale.ROOT);
    }
    private static RuntimeException conflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "products_sku_key".equals(constraint.getConstraintName())) { return new ApiFailure(409, "sku_conflict"); }
        }
        return exception;
    }
}
