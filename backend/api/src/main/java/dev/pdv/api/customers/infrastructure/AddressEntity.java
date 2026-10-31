package dev.pdv.api.customers.infrastructure;

import java.time.Instant;
import java.util.UUID;
import dev.pdv.api.customers.application.AddressInput;
import dev.pdv.api.customers.application.AddressView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "customer_addresses")
public class AddressEntity {
    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "address_type", length = 40, nullable = false) private String type;
    @Column(length = 200, nullable = false) private String street;
    @Column(name = "house_number", length = 40, nullable = false) private String number;
    @Column(length = 160) private String complement;
    @Column(length = 120, nullable = false) private String neighborhood;
    @Column(length = 120, nullable = false) private String city;
    @Column(length = 2, nullable = false) private String state;
    @Column(name = "postal_code", length = 8, nullable = false) private String postalCode;
    @Column(name = "is_primary", nullable = false) private boolean main;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false) private Instant updatedAt;

    protected AddressEntity() { }

    public AddressEntity(UUID customerId, AddressInput input) {
        id = UUID.randomUUID();
        this.customerId = customerId;
        change(input, null);
    }

    public UUID id() { return id; }

    public void change(AddressInput input, Instant now) {
        type = input.type().strip();
        street = input.street().strip();
        number = input.number().strip();
        complement = input.complement() == null || input.complement().isBlank() ? null : input.complement().strip();
        neighborhood = input.neighborhood().strip();
        city = input.city().strip();
        state = input.state();
        postalCode = input.postalCode().replace("-", "");
        main = input.primary();
        updatedAt = now;
    }

    public AddressView view() {
        return new AddressView(id, customerId, type, street, number, complement, neighborhood, city, state, postalCode,
                main, version, createdAt, updatedAt);
    }
}
