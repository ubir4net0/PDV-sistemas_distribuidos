package dev.pdv.api.customers.infrastructure;

import java.net.URI;
import dev.pdv.api.customers.application.CustomerInput;
import dev.pdv.api.customers.application.CustomerService;
import dev.pdv.api.customers.application.CustomerView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
