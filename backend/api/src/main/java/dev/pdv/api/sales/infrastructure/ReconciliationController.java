package dev.pdv.api.sales.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.sales.application.ReconciliationModels.Execution;
import dev.pdv.api.sales.application.ReconciliationModels.Reconcile;
import dev.pdv.api.sales.application.ReconciliationModels.Resolve;
import dev.pdv.api.sales.application.ReconciliationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service=service; }
    @PostMapping("/simulator/payments/{id}/resolution")
    public ResponseEntity<?> resolve(@PathVariable UUID id,@Valid @RequestBody Resolve input,@AuthenticationPrincipal AuthPrincipal actor) { return response(service.resolve(id,input,actor.id())); }
    @PostMapping("/sales/{id}/reconcile")
    public ResponseEntity<?> reconcile(@PathVariable UUID id,@Valid @RequestBody Reconcile input,@AuthenticationPrincipal AuthPrincipal actor) { return response(service.reconcile(id,input.requestId(),actor.id())); }
    private <T> ResponseEntity<T> response(Execution<T> execution) { return ResponseEntity.status(execution.status()).header("Cache-Control","no-store")
        .header("Idempotent-Replay",Boolean.toString(execution.replayed())).body(execution.response()); }
}
