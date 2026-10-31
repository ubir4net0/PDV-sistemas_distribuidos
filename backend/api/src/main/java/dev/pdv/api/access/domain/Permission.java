package dev.pdv.api.access.domain;

import java.util.UUID;

public record Permission(UUID id, String resource, String action) {
    public String code() {
        return resource + "." + action;
    }
}
