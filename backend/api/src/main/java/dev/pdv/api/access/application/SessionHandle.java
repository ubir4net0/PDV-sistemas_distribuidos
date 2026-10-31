package dev.pdv.api.access.application;

public record SessionHandle(String digest) {
    @Override
    public String toString() {
        return "SessionHandle[REDACTED]";
    }
}
