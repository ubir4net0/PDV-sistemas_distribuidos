package dev.pdv.api.purchasing.application;

import dev.pdv.api.catalog.application.CatalogModels.ActiveUpdate;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.catalog.infrastructure.CatalogAudit;
import dev.pdv.api.catalog.infrastructure.CatalogStore;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.customers.domain.CustomerDocument;
import dev.pdv.api.purchasing.application.SupplierModels.Input;
import dev.pdv.api.purchasing.application.SupplierModels.Link;
import dev.pdv.api.purchasing.application.SupplierModels.Update;
import dev.pdv.api.purchasing.application.SupplierModels.View;
import dev.pdv.api.purchasing.infrastructure.SupplierAudit;
import dev.pdv.api.purchasing.infrastructure.SupplierEntity;
import dev.pdv.api.purchasing.infrastructure.SupplierStore;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('supplier.read')")
public class SupplierService {
    private final SupplierStore store;
    private final SupplierAudit audit;
    private final CatalogStore catalog;
    private final CatalogAudit catalogAudit;
    public SupplierService(SupplierStore store, SupplierAudit audit, CatalogStore catalog, CatalogAudit catalogAudit) {
        this.store = store; this.audit = audit; this.catalog = catalog; this.catalogAudit = catalogAudit;
    }
    public View get(UUID id) { return store.require(id, false).view(); }
    public Page<View> list(Boolean active, int page, int size) { Page.validate(page, size); return store.list(active, page, size); }
    public Page<SupplierAudit.Change> history(UUID id, int page, int size) {
        Page.validate(page, size); store.require(id, false); return audit.history(id, page, size);
    }
    public Page<ProductView> products(UUID id, int page, int size) {
        Page.validate(page, size); store.require(id, false); return store.products(id, page, size);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('supplier.write')")
    public View create(Input input, UUID actor) {
        try {
            var result = store.insert(new SupplierEntity(input, cnpj(input.cnpj()))).view();
            audit.append(result.id(), actor, "CREATED", "{}"); return result;
        } catch (DataIntegrityViolationException exception) { throw conflict(exception); }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('supplier.write')")
    public View update(UUID id, Update input, UUID actor) {
        var entity = store.require(id, true); version(entity.view().version(), input.version());
        String before = audit.snapshot(id); entity.change(input.supplier(), cnpj(input.supplier().cnpj()), catalogAudit.now());
        try {
            var result = store.flush(entity).view(); audit.append(id, actor, "UPDATED", before); return result;
        } catch (DataIntegrityViolationException exception) { throw conflict(exception); }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('supplier.write')")
    public View status(UUID id, ActiveUpdate input, UUID actor) {
        var entity = store.require(id, true); version(entity.view().version(), input.version());
        if (entity.view().active() == input.active()) { return entity.view(); }
        String before = audit.snapshot(id); entity.active(input.active(), catalogAudit.now());
        var result = store.flush(entity).view(); audit.append(id, actor, input.active() ? "ACTIVATED" : "INACTIVATED", before); return result;
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('supplier.write')")
    public ProductView link(UUID id, Link input, UUID actor) {
        var supplier = input.supplierId() == null ? null : store.require(input.supplierId(), true).view();
        var entity = catalog.product(id, true); var current = entity.view(); version(current.version(), input.version());
        if (java.util.Objects.equals(current.supplierId(), input.supplierId())) { return current; }
        if (supplier != null && !supplier.active()) { throw new ApiFailure(409, "supplier_inactive"); }
        String before = catalogAudit.snapshot(id, true); entity.supplier(input.supplierId(), catalogAudit.now());
        var result = catalog.flush(entity).view(); catalogAudit.append(id, true, actor, "SUPPLIER_CHANGED", before); return result;
    }
    private static void version(long actual, long expected) { if (actual != expected) { throw new ApiFailure(409, "version_conflict"); } }
    private static String cnpj(String raw) {
        try { return CustomerDocument.canonical("PJ", raw); } catch (IllegalArgumentException exception) { throw new ApiFailure(400, "invalid_request"); }
    }
    private static RuntimeException conflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "suppliers_cnpj_key".equals(constraint.getConstraintName())) { return new ApiFailure(409, "cnpj_conflict"); }
        }
        return exception;
    }
}
