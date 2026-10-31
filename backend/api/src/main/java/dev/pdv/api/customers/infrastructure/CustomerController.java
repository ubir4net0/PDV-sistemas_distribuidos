package dev.pdv.api.customers.infrastructure;

import java.net.URI;
import java.util.UUID;
import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.customers.application.CustomerUpdate;
import dev.pdv.api.customers.application.CustomerStatus;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.customers.application.CustomerInput;
import dev.pdv.api.customers.application.CustomerService;
import dev.pdv.api.customers.application.CustomerView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CustomerView> create(@Valid @RequestBody CustomerInput input) {
        var customer = service.create(input);
        return ResponseEntity.created(URI.create("/customers/" + customer.id())).body(customer);
    }

    @GetMapping
    public Page<CustomerView> list(@RequestParam(required = false) String name, @RequestParam(required = false) String document,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(name, document, active, page, size);
    }

    @GetMapping("/{id}")
    public CustomerView get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/history")
    public Page<CustomerAudit.Change> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.history(id, page, size); }

    @PutMapping("/{id}")
    public CustomerView update(@PathVariable UUID id, @Valid @RequestBody CustomerUpdate input,
            @AuthenticationPrincipal AuthPrincipal actor) { return service.update(id, input, actor.id()); }

    @PatchMapping("/{id}/status")
    public CustomerView status(@PathVariable UUID id, @Valid @RequestBody CustomerStatus input,
            @AuthenticationPrincipal AuthPrincipal actor) { return service.status(id, input, actor.id()); }
}
