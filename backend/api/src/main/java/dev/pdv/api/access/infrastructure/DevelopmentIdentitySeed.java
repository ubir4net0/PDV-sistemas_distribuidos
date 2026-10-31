package dev.pdv.api.access.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit development profile only; never provisions a default account/password. */
@Component
@Profile("development")
public class DevelopmentIdentitySeed implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DevelopmentIdentitySeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.update("INSERT INTO roles(code, name) VALUES ('ADMIN', 'Administrador') ON CONFLICT (code) DO NOTHING");
        jdbc.update("INSERT INTO roles(code, name) VALUES ('OPERATOR', 'Operador') ON CONFLICT (code) DO NOTHING");
        jdbc.update("""
                INSERT INTO permissions(resource, action, description)
                VALUES ('access.permissions', 'read', 'Consultar catalogo de permissoes')
                ON CONFLICT (resource, action) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO role_permissions(role_id, permission_id)
                SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
                WHERE r.code = 'ADMIN' AND p.resource = 'access.permissions' AND p.action = 'read'
                ON CONFLICT (role_id, permission_id) DO NOTHING
                """);
    }
}
