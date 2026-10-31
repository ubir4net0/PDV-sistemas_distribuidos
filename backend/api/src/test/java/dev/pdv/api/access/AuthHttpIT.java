package dev.pdv.api.access;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import dev.pdv.api.access.application.TokenCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class AuthHttpIT {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final UUID id = UUID.randomUUID();
    private final String email = id + "@example.test";
    private final String password = UUID.randomUUID().toString();

    @BeforeEach
    void provisionOnlySyntheticIdentity() {
        jdbc.update("INSERT INTO users(id,name,email,password_hash,active) VALUES (?, 'Teste', ?, ?, true)",
                id, email, new BCryptPasswordEncoder(4).encode(password));
    }

    @AfterEach
    void removeOnlyOwnIdentity() {
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @Test
    void authenticatesOwnProfileStoresOnlyDigestAndDoesNotLogSecrets(CapturedOutput output) throws Exception {
        var response = login(email, password);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var payload = json.readTree(response.body());
        String token = payload.get("accessToken").asString();
        assertThat(payload.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(Instant.parse(payload.get("expiresAt").asString())).isAfter(Instant.now());
        assertThat(jdbc.queryForObject("SELECT token_hash = ? FROM auth_tokens WHERE user_id = ?",
                Boolean.class, TokenCodec.digest(token), id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, id)).isTrue();
        var profile = send("GET", "/auth/me", null, "Authorization", "Bearer " + token);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(profile.body()).get("id").asString()).isEqualTo(id.toString());
        assertThat(json.readTree(profile.body()).propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email", "roles", "permissions");
        assertThat(output.getAll().contains(password) || output.getAll().contains(token)).isFalse();
    }

    @Test
    void unknownWrongAndInactiveCredentialsHaveSameSafeError() throws Exception {
        var wrong = login(email, "incorrect");
        var unknown = login(UUID.randomUUID() + "@example.test", password);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", id);
        var inactive = login(email, password);
        for (var response : java.util.List.of(wrong, unknown, inactive)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).isEqualTo("{\"code\":\"unauthorized\"}");
            assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id = ?", Integer.class, id)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"email\":\"invalid\",\"password\":\"p\"}",
            "{\"email\":\"a@example.test\",\"password\":\"p\",\"extra\":true}",
            "{\"email\":\"a@example.test\",\"password\":null}"})
    void rejectsInvalidBodiesWithoutEchoingInput(String body) throws Exception {
        var response = send("POST", "/auth/login", body);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("{\"code\":\"invalid_request\"}");
    }

    @Test
    void rejectsMultibytePasswordOver72Bytes() throws Exception {
        assertThat(login(email, "é".repeat(37)).statusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Basic abc", "Bearer abc", "Bearer aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsMissingMalformedAndUnknownTokens(String header) throws Exception {
        var response = header.isEmpty() ? send("GET", "/auth/me", null)
                : send("GET", "/auth/me", null, "Authorization", header);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).isEqualTo("{\"code\":\"unauthorized\"}");
    }

    @Test
    void rejectsDuplicateAuthorizationAndAmbientCredentials() throws Exception {
        var token = token();
        assertThat(send("GET", "/auth/me", null, "Authorization", "Bearer " + token,
                "Authorization", "Bearer " + token).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/auth/me?access_token=" + token, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/auth/me", null, "Cookie", "access_token=" + token).statusCode()).isEqualTo(401);
    }

    @Test
    void logoutRevokesOnlyCurrentTokenAndIsEnforcedImmediately() throws Exception {
        var first = token();
        var second = token();
        assertThat(first.equals(second)).isFalse();
        assertThat(send("POST", "/auth/logout", null, "Authorization", "Bearer " + first).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/auth/me", null, "Authorization", "Bearer " + first).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/auth/me", null, "Authorization", "Bearer " + second).statusCode()).isEqualTo(200);
    }

    @Test
    void expiredTokenIsRejectedUsingDatabaseClock() throws Exception {
        var token = token();
        jdbc.update("""
                UPDATE auth_tokens SET created_at = clock_timestamp() - interval '2 hours',
                expires_at = clock_timestamp() - interval '1 hour' WHERE user_id = ?
                """, id);
        assertThat(send("GET", "/auth/me", null, "Authorization", "Bearer " + token).statusCode()).isEqualTo(401);
    }

    @Test
    void deactivatingUserInvalidatesExistingToken() throws Exception {
        var token = token();
        jdbc.update("UPDATE users SET active = false WHERE id = ?", id);
        assertThat(send("GET", "/auth/me", null, "Authorization", "Bearer " + token).statusCode()).isEqualTo(401);
    }

    @Test
    void changingPasswordInvalidatesExistingToken() throws Exception {
        var token = token();
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", new BCryptPasswordEncoder(4).encode("changed"), id);
        assertThat(send("GET", "/auth/me", null, "Authorization", "Bearer " + token).statusCode()).isEqualTo(401);
    }

    @Test
    void authenticatedUserCannotAccessUnimplementedRoutes() throws Exception {
        assertThat(send("GET", "/api/customers", null, "Authorization", "Bearer " + token()).statusCode()).isEqualTo(403);
    }

    private String token() throws Exception {
        var response = login(email, password);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("accessToken").asString();
    }

    private HttpResponse<String> login(String loginEmail, String loginPassword) throws Exception {
        return send("POST", "/auth/login", json.writeValueAsString(Map.of("email", loginEmail, "password", loginPassword)));
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
