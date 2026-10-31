package dev.pdv.api.customers.application;

import java.util.List;
import java.util.UUID;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.infrastructure.AddressEntity;
import dev.pdv.api.customers.infrastructure.AddressStore;
import dev.pdv.api.customers.infrastructure.CustomerAudit;
import dev.pdv.api.customers.infrastructure.CustomerStore;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final CustomerStore customers;
    private final AddressStore addresses;
    private final CustomerAudit audit;

    public AddressService(CustomerStore customers, AddressStore addresses, CustomerAudit audit) {
        this.customers = customers; this.addresses = addresses; this.audit = audit;
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.read')")
    @Transactional(readOnly = true)
    public Page<AddressView> list(UUID customer, int page, int size) {
        Page.validate(page, size);
        customers.require(customer, false);
        return addresses.list(customer, page, size);
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.address_write')")
    @Transactional
    public AddressView create(UUID customer, AddressInput input, UUID actor) {
        var parent = customers.require(customer, true);
        String before = addresses.snapshot(customer);
        var entity = new AddressEntity(customer, input);
        if (input.primary()) { addresses.demoteOthers(customer, entity.id()); }
        addresses.persist(entity);
        parent.touch(audit.now());
        customers.flush(parent);
        addresses.refresh(entity);
        audit.append(customer, actor, "ADDRESS_CREATED", before, addresses.snapshot(customer));
        return entity.view();
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.address_write')")
    @Transactional
    public AddressView update(UUID customer, UUID address, AddressUpdate input, UUID actor) {
        var parent = customers.require(customer, true);
        var entity = addresses.require(address, customer);
        if (entity.view().version() != input.version()) { throw new ApiFailure(409, "version_conflict"); }
        String before = addresses.snapshot(customer);
        if (input.address().primary()) { addresses.demoteOthers(customer, address); }
        entity.change(input.address(), audit.now());
        parent.touch(audit.now());
        customers.flush(parent);
        addresses.refresh(entity);
        audit.append(customer, actor, "ADDRESS_UPDATED", before, addresses.snapshot(customer));
        return entity.view();
    }

    @PreAuthorize("(hasRole('ADMIN') or hasRole('OPERATOR')) and hasAuthority('customer.read')")
    @Transactional(readOnly = true)
    public List<AddressStore.Region> regions(Boolean active, String state, String city) {
        if ((state != null && !state.matches("AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO"))
                || (city != null && city.length() > 120)) {
            throw new ApiFailure(400, "invalid_request");
        }
        return addresses.regions(active, state, city == null || city.isBlank() ? null : city.strip());
    }
}
