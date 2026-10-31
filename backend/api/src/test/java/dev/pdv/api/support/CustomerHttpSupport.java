package dev.pdv.api.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class CustomerHttpSupport {
    @Value("${local.server.port}") protected int port;
    @Autowired protected JdbcTemplate jdbc;
    protected final JsonMapper json = JsonMapper.builder().build();
    protected final List<UUID> customers = new ArrayList<>();
    protected final UUID admin = UUID.randomUUID();
    protected final UUID operator = UUID.randomUUID();
    private final UUID adminRole = UUID.randomUUID();
    private final UUID operatorRole = UUID.randomUUID();
    private final List<UUID> permissions = new ArrayList<>();
    private final String password = UUID.randomUUID().toString();
    private final HttpClient client = HttpClient.newHttpClient();

    protected List<String> actions() { return List.of("create"); }

    @BeforeEach
    void provisionOwnIdentity() {
        String hash = new BCryptPasswordEncoder(4).encode(password);
        for (var user : List.of(admin, operator)) {
            jdbc.update("INSERT INTO users(id,name,email,password_hash,active) VALUES (?,'Synthetic',?,?,true)",
                    user, user + "@example.test", hash);
        }
        jdbc.update("INSERT INTO roles(id,code,name) VALUES (?,'ADMIN','Admin'),(?,'OPERATOR','Operator')", adminRole, operatorRole);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?),(?,?)", admin, adminRole, operator, operatorRole);
        for (var action : actions()) {
            UUID permission = UUID.randomUUID();
            permissions.add(permission);
            jdbc.update("INSERT INTO permissions(id,resource,action) VALUES (?,'customer',?)", permission, action);
            jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", adminRole, permission);
            if (!"status".equals(action)) {
                jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)", operatorRole, permission);
            }
        }
    }

    @AfterEach
    void cleanupOwnObjects() {
        for (var id : customers) {
            jdbc.update("DELETE FROM customer_changes WHERE customer_id=?", id);
            jdbc.update("DELETE FROM customers WHERE id=?", id);
        }
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", admin, operator);
        jdbc.update("DELETE FROM roles WHERE id IN (?,?)", adminRole, operatorRole);
        for (var id : permissions) {
            jdbc.update("DELETE FROM permissions WHERE id=?", id);
        }
    }

    protected String token(String role) throws Exception {
        var id = "ADMIN".equals(role) ? admin : operator;
        var response = send("POST", "/auth/login", null, Map.of("email", id + "@example.test", "password", password));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("accessToken").asString();
    }

    protected Map<String, Object> input(String document) {
        return Map.of("personType", document.length() == 11 ? "PF" : "PJ", "document", document, "name", "Synthetic Customer");
    }

    protected UUID create(String token) throws Exception {
        var response = send("POST", "/customers", token, input("12345678909"));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).get("id").asString());
    }

    protected HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        if ("/customers".equals(path) && "POST".equals(method) && response.statusCode() == 201) {
            customers.add(UUID.fromString(json.readTree(response.body()).get("id").asString()));
        }
        return response;
    }
}
