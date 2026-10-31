package dev.pdv.api.catalog.infrastructure;

import dev.pdv.api.catalog.application.CatalogModels.CategoryView;
import dev.pdv.api.catalog.application.CatalogModels.ProductView;
import dev.pdv.api.catalog.application.CatalogQueryService;
import dev.pdv.api.customers.application.Page;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogQueryController {
    private final CatalogQueryService service;
    public CatalogQueryController(CatalogQueryService service) { this.service = service; }
    @GetMapping("/products")
    public Page<ProductView> products(@RequestParam(required = false) String q, @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.products(q, categoryId, status, page, size);
    }
    @GetMapping("/categories")
    public Page<CategoryView> categories(@RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.categories(active, page, size); }
}
