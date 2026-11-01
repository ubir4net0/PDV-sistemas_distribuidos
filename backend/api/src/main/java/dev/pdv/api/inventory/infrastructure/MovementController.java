package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.MovementModels.Batch;
import dev.pdv.api.inventory.application.MovementModels.Execution;
import dev.pdv.api.inventory.application.MovementModels.Movement;
import dev.pdv.api.inventory.application.MovementModels.Operation;
import dev.pdv.api.inventory.application.MovementModels.Single;
import dev.pdv.api.inventory.application.MovementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MovementController {
    private final MovementService service;
    public MovementController(MovementService service) { this.service = service; }
    @PostMapping("/inventory/{productId}/movements")
    public ResponseEntity<Operation> single(@PathVariable UUID productId, @Valid @RequestBody Single input, @AuthenticationPrincipal AuthPrincipal actor) {
        return response(service.single(productId, input, actor.id()));
    }
    @PostMapping("/inventory/movements/batches")
    public ResponseEntity<Operation> batch(@Valid @RequestBody Batch input, @AuthenticationPrincipal AuthPrincipal actor) { return response(service.batch(input, actor.id())); }
    @GetMapping("/inventory/{productId}/movements")
    public Page<Movement> history(@PathVariable UUID productId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.history(productId, page, size); }
    private static ResponseEntity<Operation> response(Execution result) {
        return ResponseEntity.status(201).header("Idempotent-Replay", Boolean.toString(result.replayed())).body(result.operation());
    }
}
