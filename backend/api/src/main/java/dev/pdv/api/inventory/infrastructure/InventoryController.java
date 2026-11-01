package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import dev.pdv.api.inventory.application.InventoryModels.Opening;
import dev.pdv.api.inventory.application.InventoryService;
import dev.pdv.api.inventory.application.InventoryPrecondition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }
    @GetMapping("/inventory/{productId}")
    public ResponseEntity<Balance> get(@PathVariable UUID productId, @RequestHeader HttpHeaders headers) {
        var balance = service.get(productId);
        InventoryPrecondition.parse(ifMatch(headers)).verify(balance);
        return ResponseEntity.ok().eTag(InventoryPrecondition.tag(balance)).header("Cache-Control", "no-store").body(balance);
    }
    static String ifMatch(HttpHeaders headers) {
        var values = headers.get(HttpHeaders.IF_MATCH); return values == null ? null : String.join(",", values);
    }
    @GetMapping("/inventory/{productId}/opening")
    public Opening opening(@PathVariable UUID productId) { return service.opening(productId); }
    @GetMapping("/inventory")
    public Page<Balance> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
}
