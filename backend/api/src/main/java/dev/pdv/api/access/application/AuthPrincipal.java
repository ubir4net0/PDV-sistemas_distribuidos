package dev.pdv.api.access.application;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

public record AuthPrincipal(UUID id, String displayName, String email,
        List<String> roles, List<String> permissions) implements Principal {
    public AuthPrincipal {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    @Override
    public String getName() {
        return id.toString();
    }

    @Override
    public String toString() {
        return "AuthPrincipal[id=" + id + ", identity=REDACTED]";
    }
}
