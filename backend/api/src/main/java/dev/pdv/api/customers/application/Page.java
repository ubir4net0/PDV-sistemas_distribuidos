package dev.pdv.api.customers.application;

import java.util.List;
import dev.pdv.api.configuration.ApiFailure;

public record Page<T>(List<T> items, long total, int page, int size) {
    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ApiFailure(400, "invalid_request");
        }
    }
}
