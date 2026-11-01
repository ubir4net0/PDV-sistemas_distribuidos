package dev.pdv.api.inventory.application;

import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import dev.pdv.api.inventory.application.InventoryModels.Opening;
import dev.pdv.api.inventory.infrastructure.InventoryStore;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('inventory.read')")
public class InventoryService {
    private final InventoryStore store;
    public InventoryService(InventoryStore store) { this.store = store; }
    public Balance get(UUID id) { return store.require(id); }
    public Opening opening(UUID id) { return store.opening(id); }
    public Page<Balance> list(int page, int size) { Page.validate(page, size); return store.list(page, size); }
}
