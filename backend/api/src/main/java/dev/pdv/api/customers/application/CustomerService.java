package dev.pdv.api.customers.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.domain.CustomerDocument;
import dev.pdv.api.customers.infrastructure.CustomerStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerStore store;

    public CustomerService(CustomerStore store) { this.store = store; }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.create')")
    @Transactional
    public CustomerView create(CustomerInput input) {
        String document = document(input);
        try {
            return store.create(input, document);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "customers_document_key".equals(constraint.getConstraintName())) {
                    throw new ApiFailure(409, "document_conflict");
                }
            }
            throw exception;
        }
    }

    private static String document(CustomerInput input) {
        try {
            return CustomerDocument.canonical(input.personType(), input.document());
        } catch (IllegalArgumentException exception) {
            throw new ApiFailure(400, "invalid_request");
        }
    }

}
