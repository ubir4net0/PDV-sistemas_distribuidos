package dev.pdv.api.customers.domain;

import java.util.Locale;

/** Local format/checksum validation, not a registration lookup. */
public final class CustomerDocument {
    private CustomerDocument() { }

    public static String canonical(String type, String input) {
        if (input == null || type == null) {
            throw new IllegalArgumentException("Invalid document");
        }
        String raw = input.strip();
        if (!raw.matches("[A-Za-z0-9./-]+")) {
            throw new IllegalArgumentException("Invalid document");
        }
        String value = raw.toUpperCase(Locale.ROOT);
        boolean cpf = "PF".equals(type);
        String format = cpf ? "(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})"
                : "(?:[A-Z0-9]{12}[0-9]{2}|[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-[0-9]{2})";
        if ((!cpf && !"PJ".equals(type)) || !value.matches(format)) {
            throw new IllegalArgumentException("Invalid document");
        }
        value = value.replace(".", "").replace("/", "").replace("-", "");
        if (value.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("Invalid document");
        }
        int length = cpf ? 9 : 12;
        for (int end = length; end < length + 2; end++) {
            int sum = 0;
            for (int index = 0; index < end; index++) {
                int weight = cpf ? end + 1 - index : 2 + (end - 1 - index) % 8;
                sum += (value.charAt(index) - '0') * weight;
            }
            int remainder = sum % 11;
            int digit = remainder < 2 ? 0 : 11 - remainder;
            if (value.charAt(end) - '0' != digit) {
                throw new IllegalArgumentException("Invalid document");
            }
        }
        return value;
    }
}
