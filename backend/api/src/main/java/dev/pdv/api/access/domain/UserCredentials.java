package dev.pdv.api.access.domain;

import java.util.UUID;

public record UserCredentials(UUID id, String name, String email, String passwordHash, boolean active) {
    @Override
    public String toString() {
        return "UserCredentials[id=" + id + ", credentials=REDACTED]";
    }
}
