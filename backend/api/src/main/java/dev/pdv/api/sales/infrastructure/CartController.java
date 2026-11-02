package dev.pdv.api.sales.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.sales.application.CartModels.Cart;
import dev.pdv.api.sales.application.CartModels.Create;
import dev.pdv.api.sales.application.CartModels.SetItem;
import dev.pdv.api.sales.application.CartService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }
    @PostMapping("/carts")
    public ResponseEntity<Cart> create(@Valid @RequestBody Create input, @AuthenticationPrincipal AuthPrincipal actor) {
        var result = service.create(input, actor.id()); return ResponseEntity.status(201).header("Cache-Control", "no-store")
                .header("Idempotent-Replay", Boolean.toString(result.replayed())).body(result.cart());
    }
    @GetMapping("/carts/{id}")
    public Cart get(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal actor) { return service.get(id, actor.id()); }
    @GetMapping("/carts")
    public Page<Cart> list(@AuthenticationPrincipal AuthPrincipal actor, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(actor.id(), page, size);
    }
    @PutMapping("/carts/{id}/items/{productId}")
    public Cart set(@PathVariable UUID id, @PathVariable UUID productId, @Valid @RequestBody SetItem input, @AuthenticationPrincipal AuthPrincipal actor) { return service.setItem(id, productId, input, actor.id()); }
    @DeleteMapping("/carts/{id}/items/{productId}")
    public Cart remove(@PathVariable UUID id, @PathVariable UUID productId, @RequestParam(required = false) Long version, @AuthenticationPrincipal AuthPrincipal actor) { return service.remove(id, productId, version, actor.id()); }
}
