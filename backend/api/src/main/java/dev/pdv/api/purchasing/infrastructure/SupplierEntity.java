package dev.pdv.api.purchasing.infrastructure;

import dev.pdv.api.purchasing.application.SupplierModels.Input;
import dev.pdv.api.purchasing.application.SupplierModels.View;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class SupplierEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 14) private String cnpj;
    @Column(name = "legal_name", nullable = false, length = 200) private String legalName;
    @Column(name = "trade_name", length = 200) private String tradeName;
    @Column(length = 200) private String contact;
    @Column(length = 32) private String phone;
    @Column(length = 320) private String email;
    @Column(length = 1000) private String address;
    private boolean active;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false) private Instant updatedAt;
    protected SupplierEntity() { }
    public SupplierEntity(Input input, String canonical) { id = UUID.randomUUID(); active = true; change(input, canonical, null); }
    public View view() { return new View(id, cnpj, legalName, tradeName, contact, phone, email, address, active, version, createdAt, updatedAt); }
    public void change(Input input, String canonical, Instant now) {
        cnpj = canonical; legalName = input.legalName().strip(); tradeName = optional(input.tradeName()); contact = optional(input.contact());
        phone = optional(input.phone()); email = optional(input.email()); address = optional(input.address()); updatedAt = now;
    }
    public void active(boolean value, Instant now) { active = value; updatedAt = now; }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
