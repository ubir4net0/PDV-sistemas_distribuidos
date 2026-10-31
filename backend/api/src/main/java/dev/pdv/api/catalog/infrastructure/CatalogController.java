package dev.pdv.api.catalog.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.catalog.application.CatalogModels.ActiveUpdate;
import dev.pdv.api.catalog.application.CatalogModels.CategoryInput;
import dev.pdv.api.catalog.application.CatalogModels.CategoryUpdate;
import dev.pdv.api.catalog.application.CatalogModels.CategoryView;
import dev.pdv.api.catalog.application.CatalogModels.ProductInput;
import dev.pdv.api.catalog.application.CatalogModels.ProductUpdate;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.catalog.application.CatalogModels.StateUpdate;
import dev.pdv.api.catalog.application.CatalogService;
import dev.pdv.api.customers.application.Page;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }
    @PostMapping("/categories")
    public ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryInput input, @AuthenticationPrincipal AuthPrincipal actor) {
        var result = service.createCategory(input, actor.id()); return ResponseEntity.created(URI.create("/categories/" + result.id())).body(result);
    }
    @PostMapping("/products")
    public ResponseEntity<ProductView> createProduct(@Valid @RequestBody ProductInput input, @AuthenticationPrincipal AuthPrincipal actor) {
        var result = service.createProduct(input, actor.id()); return ResponseEntity.created(URI.create("/products/" + result.id())).body(result);
    }
    @GetMapping("/categories/{id}") public CategoryView category(@PathVariable UUID id) { return service.category(id); }
    @GetMapping("/products/{id}") public ProductView product(@PathVariable UUID id) { return service.product(id); }
    @PutMapping("/categories/{id}")
    public CategoryView category(@PathVariable UUID id, @Valid @RequestBody CategoryUpdate input, @AuthenticationPrincipal AuthPrincipal actor) {
        return service.updateCategory(id, input, actor.id());
    }
    @PutMapping("/products/{id}")
    public ProductView product(@PathVariable UUID id, @Valid @RequestBody ProductUpdate input, @AuthenticationPrincipal AuthPrincipal actor) {
        return service.updateProduct(id, input, actor.id());
    }
    @PatchMapping("/categories/{id}/status")
    public CategoryView categoryStatus(@PathVariable UUID id, @Valid @RequestBody ActiveUpdate input, @AuthenticationPrincipal AuthPrincipal actor) {
        return service.categoryStatus(id, input, actor.id());
    }
    @PatchMapping("/products/{id}/status")
    public ProductView productStatus(@PathVariable UUID id, @Valid @RequestBody StateUpdate input, @AuthenticationPrincipal AuthPrincipal actor) {
        return service.productStatus(id, input, actor.id());
    }
    @GetMapping("/categories/{id}/history")
    public Page<CatalogAudit.Change> categoryHistory(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.history(id, false, page, size); }
    @GetMapping("/products/{id}/history")
    public Page<CatalogAudit.Change> productHistory(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.history(id, true, page, size); }
}
