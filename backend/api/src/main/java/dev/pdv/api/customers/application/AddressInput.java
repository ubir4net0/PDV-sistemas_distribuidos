package dev.pdv.api.customers.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressInput(@NotBlank @Size(max = 40) String type, @NotBlank @Size(max = 200) String street,
        @NotBlank @Size(max = 40) String number, @Size(max = 160) String complement,
        @NotBlank @Size(max = 120) String neighborhood, @NotBlank @Size(max = 120) String city,
        @NotBlank @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String state,
        @NotBlank @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String postalCode, @NotNull Boolean primary) {
    @Override
    public String toString() { return "AddressInput[REDACTED]"; }
}
