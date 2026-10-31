package dev.pdv.api.customers.infrastructure;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.customers.application.AddressInput;
import dev.pdv.api.customers.application.AddressService;
import dev.pdv.api.customers.application.AddressUpdate;
import dev.pdv.api.customers.application.AddressView;
import dev.pdv.api.customers.application.Page;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class AddressController {
    private final AddressService service;
    public AddressController(AddressService service) { this.service = service; }

    @GetMapping("/{id}/addresses")
    public Page<AddressView> list(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(id, page, size); }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<AddressView> create(@PathVariable UUID id, @Valid @RequestBody AddressInput input,
            @AuthenticationPrincipal AuthPrincipal actor) {
        var address = service.create(id, input, actor.id());
        return ResponseEntity.created(URI.create("/customers/" + id + "/addresses/" + address.id())).body(address);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    public AddressView update(@PathVariable UUID id, @PathVariable UUID addressId, @Valid @RequestBody AddressUpdate input,
            @AuthenticationPrincipal AuthPrincipal actor) { return service.update(id, addressId, input, actor.id()); }

    @GetMapping("/regions")
    public List<AddressStore.Region> regions(@RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String state, @RequestParam(required = false) String city) {
        return service.regions(active, state, city);
    }
}
