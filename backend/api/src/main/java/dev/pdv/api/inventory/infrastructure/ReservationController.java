package dev.pdv.api.inventory.infrastructure;

import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.ReservationModels.Create;
import dev.pdv.api.inventory.application.ReservationModels.Execution;
import dev.pdv.api.inventory.application.ReservationModels.Renew;
import dev.pdv.api.inventory.application.ReservationModels.Reservation;
import dev.pdv.api.inventory.application.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationController {
    private final ReservationService service;
    public ReservationController(ReservationService service) { this.service = service; }
    @PostMapping("/reservations")
    public ResponseEntity<Reservation> create(@Valid @RequestBody Create input, @AuthenticationPrincipal AuthPrincipal actor) {
        return response(service.create(input, actor.id()), 201);
    }
    @PostMapping("/reservations/{id}/renew")
    public ResponseEntity<Reservation> renew(@PathVariable UUID id, @Valid @RequestBody Renew input, @AuthenticationPrincipal AuthPrincipal actor) {
        return response(service.renew(id, input, actor.id()), 200);
    }
    @GetMapping("/reservations/{id}")
    public Reservation get(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal actor) { return service.get(id, actor.id()); }
    @GetMapping("/reservations")
    public Page<Reservation> list(@AuthenticationPrincipal AuthPrincipal actor, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(actor.id(), page, size);
    }
    private static ResponseEntity<Reservation> response(Execution result, int status) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").header("Idempotent-Replay", Boolean.toString(result.replayed())).body(result.reservation());
    }
}
