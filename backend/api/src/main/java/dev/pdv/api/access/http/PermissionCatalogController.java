package dev.pdv.api.access.http;

import java.util.List;
import dev.pdv.api.access.application.PermissionCatalog;
import dev.pdv.api.access.domain.Permission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PermissionCatalogController {
    private final PermissionCatalog catalog;

    public PermissionCatalogController(PermissionCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/access/permissions")
    public List<Permission> permissions() {
        return catalog.read();
    }
}
