package dev.pdv.api.customers.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerInput(@NotBlank @Pattern(regexp = "PF|PJ") String personType,
        @NotBlank @Size(max = 18) String document, @NotBlank @Size(max = 200) String name,
        @Size(max = 32) String phone, @Email @Size(max = 320) String email, @Size(max = 160) String locality) {
    @Override
    public String toString() { return "CustomerInput[REDACTED]"; }
}
