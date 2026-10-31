package dev.pdv.api.access.application;

import java.util.List;
import dev.pdv.api.access.domain.Permission;
import dev.pdv.api.access.infrastructure.IdentityRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionCatalog {
    private final IdentityRepository identities;

    public PermissionCatalog(IdentityRepository identities) {
        this.identities = identities;
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('access.permissions.read')")
    @Transactional(readOnly = true)
    public List<Permission> read() {
        return identities.permissions();
    }
}
