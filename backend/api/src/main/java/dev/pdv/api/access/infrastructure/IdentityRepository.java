package dev.pdv.api.access.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dev.pdv.api.access.domain.Permission;
import dev.pdv.api.access.domain.UserCredentials;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private static final RowMapper<UserCredentials> USER = (row, index) -> new UserCredentials(
            row.getObject("id", UUID.class), row.getString("name"), row.getString("email"),
            row.getString("password_hash"), row.getBoolean("active"));
    private final JdbcTemplate jdbc;

    public IdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserCredentials> findByEmail(String email) {
        return jdbc.query("SELECT id, name, email, password_hash, active FROM users WHERE email = lower(btrim(?))",
                USER, email).stream().findFirst();
    }

    public Optional<UserCredentials> findById(UUID id) {
        return jdbc.query("SELECT id, name, email, password_hash, active FROM users WHERE id = ?",
                USER, id).stream().findFirst();
    }

    public List<String> roles(UUID userId) {
        return jdbc.queryForList("""
                SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ? ORDER BY r.code
                """, String.class, userId);
    }

    public Optional<UserCredentials> lockByEmail(String email) {
        return jdbc.query("SELECT id, name, email, password_hash, active FROM users WHERE email = lower(btrim(?)) FOR UPDATE",
                USER, email).stream().findFirst();
    }

    public List<String> authorities(UUID userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.resource || '.' || p.action AS authority
                FROM permissions p JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN user_roles ur ON ur.role_id = rp.role_id
                WHERE ur.user_id = ? ORDER BY authority
                """, String.class, userId);
    }

    public List<Permission> permissions() {
        return jdbc.query("SELECT id, resource, action FROM permissions ORDER BY resource, action",
                (row, index) -> new Permission(row.getObject("id", UUID.class),
                        row.getString("resource"), row.getString("action")));
    }
}
