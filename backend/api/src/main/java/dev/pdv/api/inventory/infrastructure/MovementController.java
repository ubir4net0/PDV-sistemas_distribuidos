package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.MovementModels.Batch;
import dev.pdv.api.inventory.application.MovementModels.Conditional;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import dev.pdv.api.inventory.application.InventoryPrecondition;
import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.inventory.application.MovementModels.Execution;
import dev.pdv.api.inventory.application.MovementModels.Movement;
import dev.pdv.api.inventory.application.MovementModels.Operation;
import dev.pdv.api.inventory.application.MovementModels.Single;
import dev.pdv.api.inventory.application.MovementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MovementController {
    private final MovementService service;
    public MovementController(MovementService service) { this.service = service; }
    @PostMapping("/inventory/{productId}/movements")
    public ResponseEntity<Operation> single(@PathVariable UUID productId, @Valid @RequestBody Single input, @AuthenticationPrincipal AuthPrincipal actor,
            @RequestHeader HttpHeaders headers) {
        rejectUnusedCondition(headers);
        return response(service.single(productId, input, actor.id()));
    }
    @PostMapping("/inventory/movements/batches")
    public ResponseEntity<Operation> batch(@Valid @RequestBody Batch input, @AuthenticationPrincipal AuthPrincipal actor, @RequestHeader HttpHeaders headers) {
        rejectUnusedCondition(headers); return response(service.batch(input, actor.id()));
    }
    @PatchMapping("/inventory/{productId}")
    public ResponseEntity<Balance> conditional(@PathVariable UUID productId, @Valid @RequestBody Conditional input,
            @AuthenticationPrincipal AuthPrincipal actor, @RequestHeader HttpHeaders headers) {
        var result = service.conditional(productId, input, actor.id(), InventoryController.ifMatch(headers));
        var balance = result.operation().items().getFirst().balance();
        var response = ResponseEntity.ok().header("Cache-Control", "no-store").header("Idempotent-Replay", Boolean.toString(result.replayed()));
        if (!result.replayed()) { response.eTag(InventoryPrecondition.tag(balance)); }
        return response.body(balance);
    }
    @GetMapping("/inventory/{productId}/movements")
    public Page<Movement> history(@PathVariable UUID productId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.history(productId, page, size); }
    private static ResponseEntity<Operation> response(Execution result) {
        return ResponseEntity.status(201).header("Idempotent-Replay", Boolean.toString(result.replayed())).body(result.operation());
    }
    private static void rejectUnusedCondition(HttpHeaders headers) {
        if (headers.containsHeader(HttpHeaders.IF_MATCH)) { throw new ApiFailure(400, "invalid_request"); }
    }
}
