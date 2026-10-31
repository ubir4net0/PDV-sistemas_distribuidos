package dev.pdv.api.customers.infrastructure;

import java.time.Instant;
import java.util.UUID;
import dev.pdv.api.customers.application.CustomerInput;
import dev.pdv.api.customers.application.CustomerView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "customers")
public class CustomerEntity {
    @Id private UUID id;
    @Column(name = "person_type", length = 2, nullable = false) private String personType;
    @Column(length = 14, nullable = false) private String document;
    @Column(length = 200, nullable = false) private String name;
    @Column(length = 32) private String phone;
    @Column(length = 320) private String email;
    @Column(length = 160) private String locality;
    @Column(nullable = false) private boolean active;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false) private Instant updatedAt;

    protected CustomerEntity() { }

    public CustomerEntity(CustomerInput input, String canonicalDocument) {
        id = UUID.randomUUID();
        personType = input.personType();
        document = canonicalDocument;
        name = input.name().strip();
        phone = optional(input.phone());
        email = optional(input.email());
        locality = optional(input.locality());
        active = true;
    }

    public CustomerView view() {
        return new CustomerView(id, personType, document, name, phone, email, locality, active, version, createdAt, updatedAt);
    }

    public void change(CustomerInput input, String canonicalDocument, Instant now) {
        personType = input.personType();
        document = canonicalDocument;
        name = input.name().strip();
        phone = optional(input.phone());
        email = optional(input.email());
        locality = optional(input.locality());
        touch(now);
    }

    public void status(boolean value, Instant now) { active = value; touch(now); }
    public void touch(Instant now) { updatedAt = now; }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
