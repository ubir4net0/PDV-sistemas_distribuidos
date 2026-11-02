package dev.pdv.api.access;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RbacHttpIT {
    @Test void missingQueryAndUnsupportedMediaUseSafeContractErrors() throws Exception {
        var missing=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/suppliers/"+UUID.randomUUID()))
            .header("Authorization","Bearer "+token("ADMIN")).DELETE().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(400); assertThat(json.readTree(missing.body()).get("code").asString()).isEqualTo("invalid_request");
        var media=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/auth/login"))
            .header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofString("not-json")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(media.statusCode()).isEqualTo(415); assertThat(json.readTree(media.body()).get("code").asString()).isEqualTo("unsupported_media_type");
        assertThat(media.body()).doesNotContain("Exception","password","stackTrace");
    }
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final UUID admin = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private final UUID adminRole = UUID.randomUUID();
    private final UUID operatorRole = UUID.randomUUID();
    private final UUID readPermission = UUID.randomUUID();
    private final UUID extraPermission = UUID.randomUUID();
    private final String password = UUID.randomUUID().toString();

    @BeforeEach
    void provisionSyntheticMatrix() {
        String hash = new BCryptPasswordEncoder(4).encode(password);
        for (var user : new UUID[]{admin, operator}) {
            jdbc.update("INSERT INTO users(id,name,email,password_hash,active) VALUES (?,'Synthetic',?,?,true)",
                    user, user + "@example.test", hash);
        }
        jdbc.update("INSERT INTO roles(id,code,name) VALUES (?,'ADMIN','Administrador'),(?,'OPERATOR','Operador')",
                adminRole, operatorRole);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?),(?,?)", admin, adminRole, operator, operatorRole);
        jdbc.update("""
                INSERT INTO permissions(id,resource,action) VALUES (?,'access.permissions','read'),(?,'synthetic.catalog','read')
                """, readPermission, extraPermission);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", adminRole, readPermission);
    }

    @AfterEach
    void cleanupOnlyOwnSyntheticObjects() {
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", admin, operator);
        jdbc.update("DELETE FROM roles WHERE id IN (?,?)", adminRole, operatorRole);
        jdbc.update("DELETE FROM permissions WHERE id IN (?,?)", readPermission, extraPermission);
    }

    @ParameterizedTest
    @CsvSource({"ANONYMOUS,401", "OPERATOR,403", "ADMIN,200"})
    void catalogRequiresAdministratorAndExplicitPermission(String role, int status) throws Exception {
        var response = send("GET", "/access/permissions", token(role), null);
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        if (status == 200) {
            var body = json.readTree(response.body());
            assertThat(body.size()).isEqualTo(2);
            assertThat(body.get(0).get("id").asString()).isEqualTo(readPermission.toString());
            assertThat(body.get(1).get("id").asString()).isEqualTo(extraPermission.toString());
        } else {
            assertThat(response.body()).isEqualTo(status == 401
                    ? "{\"code\":\"unauthorized\"}" : "{\"code\":\"forbidden\"}");
            assertThat(response.headers().firstValue("WWW-Authenticate").isPresent()).isEqualTo(status == 401);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANONYMOUS", "OPERATOR", "ADMIN"})
    void healthAndLoginRemainPublicForAllRoles(String role) throws Exception {
        var token = token(role);
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            assertThat(send("GET", path, token, null).statusCode()).isEqualTo(200);
        }
        var response = send("POST", "/auth/login", token,
                json.writeValueAsString(Map.of("email", operator + "@example.test", "password", password)));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "ADMIN"})
    void authenticatedRolesOnlyReadOwnProfileAndRevokeOwnCurrentToken(String role) throws Exception {
        UUID own = "ADMIN".equals(role) ? admin : operator;
        UUID other = "ADMIN".equals(role) ? operator : admin;
        var token = token(role);
        var otherToken = token("ADMIN".equals(role) ? "OPERATOR" : "ADMIN");
        var profile = send("GET", "/auth/me?user_id=" + other, token, null);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(profile.body()).get("id").asString()).isEqualTo(own.toString());
        assertThat(send("POST", "/auth/logout?user_id=" + other, token, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/auth/me", token, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/auth/me", otherToken, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/auth/logout", null, null).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/customers", "POST,/access/permissions", "PUT,/access/permissions", "DELETE,/access/permissions",
            "GET,/auth/login", "POST,/auth/me", "GET,/auth/logout", "POST,/actuator/health", "GET,/actuator/env", "GET,/"})
    void unapprovedMethodsAndPathsStayDeniedForEveryRole(String method, String path) throws Exception {
        assertThat(send(method, path, null, null).statusCode()).isEqualTo(401);
        for (String role : new String[]{"OPERATOR", "ADMIN"}) {
            var response = send(method, path, token(role), null);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).isEqualTo("{\"code\":\"forbidden\"}");
        }
    }

    @Test
    void administratorWithoutGrantIsDeniedByMethodSecurity() throws Exception {
        var token = token("ADMIN");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=? AND permission_id=?", adminRole, readPermission);
        assertThat(send("GET", "/access/permissions", token, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/auth/me", token, null).statusCode()).isEqualTo(200);
    }

    @Test
    void operatorWithAccidentalGrantDoesNotBecomeAdministrator() throws Exception {
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", operatorRole, readPermission);
        assertThat(send("GET", "/access/permissions", token("OPERATOR"), null).statusCode()).isEqualTo(403);
    }

    @Test
    void removingRoleAppliesOnNextRequestWithoutReissuingToken() throws Exception {
        var token = token("ADMIN");
        assertThat(send("GET", "/access/permissions", token, null).statusCode()).isEqualTo(200);
        jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?", admin, adminRole);
        assertThat(send("GET", "/access/permissions", token, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/auth/me", token, null).statusCode()).isEqualTo(200);
    }

    @Test
    void removingAndRestoringGrantAppliesOnNextRequestWithoutReissuingToken() throws Exception {
        var token = token("ADMIN");
        assertThat(send("GET", "/access/permissions", token, null).statusCode()).isEqualTo(200);
        jdbc.update("DELETE FROM role_permissions WHERE role_id=? AND permission_id=?", adminRole, readPermission);
        assertThat(send("GET", "/access/permissions", token, null).statusCode()).isEqualTo(403);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", adminRole, readPermission);
        assertThat(send("GET", "/access/permissions", token, null).statusCode()).isEqualTo(200);
    }

    @Test
    void everyApplicationControllerMappingRequiresAnExplicitMatrixEntry() {
        var actual = new TreeSet<String>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("dev.pdv.api")) {
                assertThat(mapping.getMethodsCondition().getMethods()).as("Explicit HTTP methods required").isNotEmpty();
                mapping.getMethodsCondition().getMethods().forEach(method ->
                        mapping.getPatternValues().forEach(path -> actual.add(method + " " + path)));
            }
        });
        assertThat(actual).containsExactly("DELETE /carts/{id}/items/{productId}", "DELETE /suppliers/{id}", "GET /access/permissions", "GET /auth/me", "GET /carts", "GET /carts/{id}",
                "GET /categories", "GET /categories/{id}", "GET /categories/{id}/history", "GET /customers", "GET /customers/regions",
                "GET /customers/{id}", "GET /customers/{id}/addresses", "GET /customers/{id}/history",
                "GET /inventory", "GET /inventory/{productId}", "GET /inventory/{productId}/movements", "GET /inventory/{productId}/opening",
                "GET /operations/telemetry", "GET /products", "GET /products/{id}", "GET /products/{id}/history", "GET /reservations", "GET /reservations/{id}", "GET /sales/{id}", "GET /sales/{id}/receipt", "GET /sales/{id}/timeline", "GET /suppliers", "GET /suppliers/{id}",
                "GET /suppliers/{id}/history", "GET /suppliers/{id}/products", "PATCH /categories/{id}/status", "PATCH /customers/{id}/status",
                "PATCH /inventory/{productId}", "PATCH /products/{id}/status", "PATCH /suppliers/{id}/status", "POST /auth/login", "POST /auth/logout", "POST /carts", "POST /carts/{id}/checkout", "POST /categories",
                "POST /customers", "POST /customers/{id}/addresses", "POST /inventory/movements/batches", "POST /inventory/{productId}/movements",
                "POST /products", "POST /reservations", "POST /reservations/{id}/renew", "POST /sales/{id}/reconcile", "POST /simulator/payments/{id}/resolution", "POST /suppliers", "PUT /carts/{id}/items/{productId}", "PUT /categories/{id}",
                "PUT /customers/{id}", "PUT /customers/{id}/addresses/{addressId}", "PUT /products/{id}", "PUT /products/{id}/supplier", "PUT /suppliers/{id}");
    }

    private String token(String role) throws Exception {
        if ("ANONYMOUS".equals(role)) {
            return null;
        }
        UUID user = "ADMIN".equals(role) ? admin : operator;
        var response = send("POST", "/auth/login", null,
                json.writeValueAsString(Map.of("email", user + "@example.test", "password", password)));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("accessToken").asString();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
