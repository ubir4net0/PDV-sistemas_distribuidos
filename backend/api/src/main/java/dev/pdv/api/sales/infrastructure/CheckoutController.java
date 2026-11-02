package dev.pdv.api.sales.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.sales.application.CheckoutModels.Checkout;
import dev.pdv.api.sales.application.CheckoutModels.Sale;
import dev.pdv.api.sales.application.CheckoutService;
import dev.pdv.api.sales.application.Receipt;
import dev.pdv.api.sales.application.SaleTimeline;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {
    private final CheckoutService service;
    public CheckoutController(CheckoutService service) { this.service = service; }
    @PostMapping("/carts/{id}/checkout")
    public ResponseEntity<Sale> checkout(@PathVariable UUID id, @Valid @RequestBody Checkout input,
            @RequestHeader(name = "Idempotency-Key", required = false) List<String> keys, @AuthenticationPrincipal AuthPrincipal actor) {
        String key = keys != null && keys.size()==1 ? keys.getFirst() : null;
        var result = service.checkout(id, input, key, actor.id());
        return ResponseEntity.status(201).header("Cache-Control", "no-store").header("Idempotent-Replay", Boolean.toString(result.replayed())).body(result.sale());
    }
    @GetMapping("/sales/{id}")
    public Sale get(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal actor) { return service.get(id, actor.id()); }
    @GetMapping("/sales/{id}/receipt")
    public ResponseEntity<Receipt> receipt(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal actor) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.receipt(id, actor.id()));
    }
    @GetMapping("/sales/{id}/timeline")
    public ResponseEntity<SaleTimeline> timeline(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal actor) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.timeline(id, actor.id()));
    }
}
