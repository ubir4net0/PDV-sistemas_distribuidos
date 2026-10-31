package dev.pdv.api.access.infrastructure;

import java.util.UUID;
import dev.pdv.api.access.http.LoginRequest;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit local opt-in; never updates or elevates an existing identity. */
@Component
@Profile("development")
@ConditionalOnProperty(name = "PDV_DEV_BOOTSTRAP_USER", havingValue = "true")
public class DevelopmentUserBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final LoginRequest credentials;
    private final Validator validator;

    public DevelopmentUserBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder, Validator validator,
            @Value("${PDV_DEV_EMAIL}") String email, @Value("${PDV_DEV_PASSWORD}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.validator = validator;
        this.credentials = new LoginRequest(email, password);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!validator.validate(credentials).isEmpty()) {
            throw new IllegalArgumentException("Invalid private development credentials");
        }
        jdbc.update("INSERT INTO roles(code, name) VALUES ('ADMIN', 'Administrador') ON CONFLICT (code) DO NOTHING");
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO users(id, name, email, password_hash, active)
                VALUES (?, 'Administrador local', lower(btrim(?)), ?, true) ON CONFLICT (email) DO NOTHING
                """, id, credentials.email(), encoder.encode(credentials.password()));
        if (inserted != 1) {
            throw new IllegalStateException("Development identity already exists; disable bootstrap before restarting");
        }
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'ADMIN'", id);
    }
}
