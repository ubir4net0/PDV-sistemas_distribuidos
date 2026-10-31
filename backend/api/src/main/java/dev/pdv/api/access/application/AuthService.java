package dev.pdv.api.access.application;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import dev.pdv.api.access.infrastructure.IdentityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final IdentityRepository identities;
    private final JdbcTemplate jdbc;
    private final AuthenticationManager manager;
    private final long ttl;

    public AuthService(IdentityRepository identities, JdbcTemplate jdbc, AuthenticationManager manager,
            @Value("${pdv.auth.token-ttl-seconds}") long ttl) {
        if (ttl <= 0 || ttl > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Token TTL must be a positive integer in seconds");
        }
        this.identities = identities;
        this.jdbc = jdbc;
        this.manager = manager;
        this.ttl = ttl;
    }

    @Transactional
    public IssuedToken login(String email, String password) {
        var locked = identities.lockByEmail(email);
        var authenticated = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        var user = locked.orElseThrow(AuthService::invalid);
        if (!user.id().toString().equals(authenticated.getName())) {
            throw invalid();
        }
        Instant created = jdbc.queryForObject("SELECT clock_timestamp()",
                (row, index) -> row.getTimestamp(1).toInstant());
        Instant expires = created.plusSeconds(ttl);
        String raw = TokenCodec.generate();
        jdbc.update("""
                INSERT INTO auth_tokens(token_hash, user_id, credential_fingerprint, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, TokenCodec.digest(raw), user.id(), TokenCodec.digest(user.passwordHash()),
                java.sql.Timestamp.from(created), java.sql.Timestamp.from(expires));
        jdbc.update("UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(created), java.sql.Timestamp.from(created), user.id());
        return new IssuedToken(raw, "Bearer", expires);
    }

    @Transactional(readOnly = true)
    public AuthPrincipal resolve(String digest) {
        var rows = jdbc.query("""
                SELECT t.user_id, t.credential_fingerprint FROM auth_tokens t JOIN users u ON u.id = t.user_id
                WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > clock_timestamp() AND u.active
                """, (row, index) -> new TokenIdentity(row.getObject(1, UUID.class), row.getString(2)), digest);
        if (rows.size() != 1) {
            throw invalid();
        }
        var token = rows.getFirst();
        var user = identities.findById(token.userId()).orElseThrow(AuthService::invalid);
        if (!user.active() || !MessageDigest.isEqual(token.fingerprint().getBytes(StandardCharsets.US_ASCII),
                TokenCodec.digest(user.passwordHash()).getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        return new AuthPrincipal(user.id(), user.name(), user.email(),
                identities.roles(user.id()), identities.authorities(user.id()));
    }

    @Transactional
    public void logout(UUID userId, SessionHandle handle) {
        jdbc.update("""
                UPDATE auth_tokens SET revoked_at = clock_timestamp()
                WHERE token_hash = ? AND user_id = ? AND revoked_at IS NULL
                """, handle.digest(), userId);
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid credentials");
    }

    private record TokenIdentity(UUID userId, String fingerprint) { }

    public record IssuedToken(String accessToken, String tokenType, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[REDACTED]";
        }
    }
}
