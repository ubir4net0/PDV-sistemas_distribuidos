package dev.pdv.api.purchasing.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.catalog.application.CatalogModels.ActiveUpdate;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.purchasing.application.SupplierModels.Input;
import dev.pdv.api.purchasing.application.SupplierModels.Link;
import dev.pdv.api.purchasing.application.SupplierModels.Update;
import dev.pdv.api.purchasing.application.SupplierModels.View;
import dev.pdv.api.purchasing.application.SupplierService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service) { this.service = service; }
    @PostMapping("/suppliers")
    public ResponseEntity<View> create(@Valid @RequestBody Input input, @AuthenticationPrincipal AuthPrincipal actor) {
        var result = service.create(input, actor.id()); return ResponseEntity.created(URI.create("/suppliers/" + result.id())).body(result);
    }
    @GetMapping("/suppliers")
    public Page<View> list(@RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(active, page, size); }
    @GetMapping("/suppliers/{id}") public View get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/suppliers/{id}")
    public View update(@PathVariable UUID id, @Valid @RequestBody Update input, @AuthenticationPrincipal AuthPrincipal actor) { return service.update(id, input, actor.id()); }
    @PatchMapping("/suppliers/{id}/status")
    public View status(@PathVariable UUID id, @Valid @RequestBody ActiveUpdate input, @AuthenticationPrincipal AuthPrincipal actor) { return service.status(id, input, actor.id()); }
    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long version, @AuthenticationPrincipal AuthPrincipal actor) {
        if (version < 0) { throw new ApiFailure(400, "invalid_request"); }
        service.status(id, new ActiveUpdate(false, version), actor.id()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/suppliers/{id}/history")
    public Page<SupplierAudit.Change> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.history(id, page, size); }
    @GetMapping("/suppliers/{id}/products")
    public Page<ProductView> products(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.products(id, page, size); }
    @PutMapping("/products/{id}/supplier")
    public ProductView link(@PathVariable UUID id, @Valid @RequestBody Link input, @AuthenticationPrincipal AuthPrincipal actor) { return service.link(id, input, actor.id()); }
}
