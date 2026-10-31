package dev.pdv.api.access;

import dev.pdv.api.access.application.TokenCodec;
import dev.pdv.api.access.http.LoginRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationTest {
    @Test
    void bcryptSaltsAndVerifiesWithoutAcceptingWrongPassword() {
        var encoder = new BCryptPasswordEncoder(4);
        var first = encoder.encode("synthetic-password");
        assertThat(encoder.encode("synthetic-password").equals(first)).isFalse();
        assertThat(encoder.matches("synthetic-password", first)).isTrue();
        assertThat(encoder.matches("wrong", first)).isFalse();
    }

    @Test
    void rejectsUtf8PasswordsExceedingBcryptByteBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new LoginRequest("unit@example.test", "é".repeat(36)))).isEmpty();
            assertThat(validator.validate(new LoginRequest("unit@example.test", "é".repeat(37)))).isNotEmpty();
            assertThat(validator.validate(new LoginRequest("unit@example.test", "a".repeat(73)))).isNotEmpty();
        }
    }

    @Test
    void tokensAreRandomAndOnlyTheirDeterministicDigestIsStored() {
        var first = TokenCodec.generate();
        assertThat(first.matches("[A-Za-z0-9_-]{43}")).isTrue();
        assertThat(first.equals(TokenCodec.generate())).isFalse();
        assertThat(TokenCodec.digest(first).matches("[a-f0-9]{64}")).isTrue();
        assertThat(TokenCodec.digest(first).equals(TokenCodec.digest(first))).isTrue();
        assertThat(TokenCodec.digest(first).contains(first)).isFalse();
    }
}
