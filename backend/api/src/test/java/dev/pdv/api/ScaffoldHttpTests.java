package dev.pdv.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScaffoldHttpTests {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ApplicationContext context;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void exposesOnlyMinimalHealth(String path) throws Exception {
        var response = send("GET", path);
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> payload = JsonMapper.builder().build().readValue(response.body(), Map.class);
        assertThat(payload.get("status")).isEqualTo("UP");
        assertThat(payload.keySet()).allSatisfy(key ->
                assertThat(key.toString()).isIn("status", "groups"));
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/actuator", "/actuator/env", "/login", "/api/customers"})
    void deniesNonHealthPathsWithoutLoginOrSession(String path) throws Exception {
        var response = send("GET", path);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @Test
    void doesNotAllowMutationsOnHealth() throws Exception {
        var response = send("POST", "/actuator/health");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void doesNotCreateDefaultInMemoryAccounts() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
