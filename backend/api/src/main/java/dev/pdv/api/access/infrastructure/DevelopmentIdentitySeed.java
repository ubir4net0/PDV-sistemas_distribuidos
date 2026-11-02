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
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('catalog','read'),('catalog','write') ON CONFLICT (resource,action) DO NOTHING");
        // Grants are applied after the roles have been created below.
        jdbc.update("INSERT INTO roles(code, name) VALUES ('ADMIN', 'Administrador') ON CONFLICT (code) DO NOTHING");
        jdbc.update("INSERT INTO roles(code, name) VALUES ('OPERATOR', 'Operador') ON CONFLICT (code) DO NOTHING");
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('reservation','read'),('reservation','write') ON CONFLICT (resource,action) DO NOTHING");
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE p.resource='reservation' AND r.code IN ('ADMIN','OPERATOR')
                ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('inventory','read') ON CONFLICT (resource,action) DO NOTHING");
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('inventory','write') ON CONFLICT (resource,action) DO NOTHING");
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE p.resource='inventory' AND p.action='write'
                AND r.code='ADMIN' ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE p.resource='inventory' AND p.action='read'
                AND r.code IN ('ADMIN','OPERATOR') ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('supplier','read'),('supplier','write') ON CONFLICT (resource,action) DO NOTHING");
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE p.resource='supplier'
                AND ((r.code='ADMIN' AND p.action IN ('read','write')) OR (r.code='OPERATOR' AND p.action='read'))
                ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE p.resource='catalog'
                AND ((r.code='ADMIN' AND p.action IN ('read','write')) OR (r.code='OPERATOR' AND p.action='read'))
                ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
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
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('customer','create') ON CONFLICT (resource,action) DO NOTHING");
        jdbc.update("INSERT INTO permissions(resource,action) VALUES ('customer','address_write') ON CONFLICT (resource,action) DO NOTHING");
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
                WHERE r.code IN ('ADMIN','OPERATOR') AND p.resource='customer' AND p.action='address_write'
                ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO permissions(resource,action) VALUES ('customer','read'),('customer','update'),('customer','status')
                ON CONFLICT (resource,action) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE p.resource='customer'
                AND ((r.code='ADMIN' AND p.action IN ('read','update','status'))
                    OR (r.code='OPERATOR' AND p.action IN ('read','update')))
                ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO role_permissions(role_id,permission_id)
                SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
                WHERE r.code IN ('ADMIN','OPERATOR') AND p.resource='customer' AND p.action='create'
                ON CONFLICT (role_id,permission_id) DO NOTHING
                """);
    }
}
