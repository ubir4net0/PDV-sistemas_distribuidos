package dev.pdv.api.customers.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.domain.CustomerDocument;
import dev.pdv.api.customers.infrastructure.CustomerStore;
import dev.pdv.api.customers.infrastructure.CustomerAudit;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerStore store;
    private final CustomerAudit audit;

    public CustomerService(CustomerStore store, CustomerAudit audit) { this.store = store; this.audit = audit; }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.create')")
    @Transactional
    public CustomerView create(CustomerInput input) {
        String document = document(input);
        try {
            return store.create(input, document);
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.read')")
    @Transactional(readOnly = true)
    public Page<CustomerView> list(String name, String document, Boolean active, int page, int size) {
        Page.validate(page, size);
        String filter = null;
        if (document != null && !document.isBlank()) {
            String compact = document.replace(".", "").replace("-", "").replace("/", "").strip();
            try {
                filter = CustomerDocument.canonical(compact.length() == 11 ? "PF" : "PJ", document);
            } catch (IllegalArgumentException exception) {
                throw new ApiFailure(400, "invalid_request");
            }
        }
        if (name != null && name.length() > 200) {
            throw new ApiFailure(400, "invalid_request");
        }
        return store.list(name == null ? "" : name.strip(), filter, active, page, size);
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.read')")
    @Transactional(readOnly = true)
    public CustomerView get(UUID id) { return store.require(id, false).view(); }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.read')")
    @Transactional(readOnly = true)
    public Page<CustomerAudit.Change> history(UUID id, int page, int size) {
        Page.validate(page, size);
        store.require(id, false);
        return audit.history(id, page, size);
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.update')")
    @Transactional
    public CustomerView update(UUID id, CustomerUpdate input, UUID actor) {
        var entity = store.require(id, true);
        version(entity.view().version(), input.version());
        String before = audit.snapshot(id);
        entity.change(input.customer(), document(input.customer()), audit.now());
        CustomerView updated;
        try {
            updated = store.flush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw conflict(exception);
        }
        audit.append(id, actor, "UPDATED", before, audit.snapshot(id));
        return updated;
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('customer.status')")
    @Transactional
    public CustomerView status(UUID id, CustomerStatus input, UUID actor) {
        var entity = store.require(id, true);
        version(entity.view().version(), input.version());
        if (entity.view().active() == input.active()) {
            return entity.view();
        }
        String before = audit.snapshot(id);
        entity.status(input.active(), audit.now());
        var result = store.flush(entity);
        audit.append(id, actor, input.active() ? "ACTIVATED" : "INACTIVATED", before, audit.snapshot(id));
        return result;
    }

    private static void version(long actual, long expected) {
        if (actual != expected) { throw new ApiFailure(409, "version_conflict"); }
    }

    private static RuntimeException conflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "customers_document_key".equals(constraint.getConstraintName())) {
                return new ApiFailure(409, "document_conflict");
            }
        }
        return exception;
    }

    private static String document(CustomerInput input) {
        try {
            return CustomerDocument.canonical(input.personType(), input.document());
        } catch (IllegalArgumentException exception) {
            throw new ApiFailure(400, "invalid_request");
        }
    }

}
