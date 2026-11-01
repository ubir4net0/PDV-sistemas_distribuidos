package dev.pdv.api.inventory;

import dev.pdv.api.support.MovementHttpSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryConditionalHttpIT extends MovementHttpSupport {
    private final HttpClient conditionalClient = HttpClient.newHttpClient();
    private Map<String, Object> patch(UUID request) { return Map.of("requestId", request, "type", "ENTRY", "quantity", 1, "reason", "Synthetic conditional movement"); }
    private HttpResponse<String> request(String method, String path, String token, Object body, String condition) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        if (condition != null) { builder.header("If-Match", condition); }
        if (body != null) { builder.header("Content-Type", "application/json"); }
        return conditionalClient.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String tag(UUID product, long version) { return "\"inventory-" + product + "-v" + version + "\""; }
    @Test
    void strongTagsConditionalWriteAndKnownReplayReturnSnapshotsWithoutDuplicateEffect() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product;
        var read = request("GET", path, token, null, null); assertThat(read.headers().firstValue("ETag")).contains(tag(product, 0));
        assertThat(read.headers().firstValue("Cache-Control")).contains("no-store");
        var input = patch(UUID.randomUUID()); var first = request("PATCH", path, token, input, tag(product, 0));
        assertThat(first.statusCode()).isEqualTo(200); assertThat(first.headers().firstValue("ETag")).contains(tag(product, 1));
        var next = request("PATCH", path, token, patch(UUID.randomUUID()), tag(product, 1)); assertThat(next.statusCode()).isEqualTo(200);
        var replay = request("PATCH", path, token, input, tag(product, 0));
        assertThat(replay.statusCode()).isEqualTo(200); assertThat(replay.body()).isEqualTo(first.body());
        assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true"); assertThat(replay.headers().firstValue("ETag")).isEmpty();
        assertThat(physical(product)).isEqualTo(2);
        var changed = new HashMap<>(input); changed.put("quantity", 2);
        assertThat(request("PATCH", path, token, changed, tag(product, 0)).statusCode()).isEqualTo(409);
    }
    @Test
    void staleWeakAndForeignTagsFailWhileListsAndQuotedCommasAreParsedStrongly() throws Exception {
        var token = token("ADMIN"); var category = category(token); var product = product(token, category); var other = product(token, category); String path = "/inventory/" + product;
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), tag(other, 0)).statusCode()).isEqualTo(412);
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), "W/" + tag(product, 0)).statusCode()).isEqualTo(412);
        String list = "\"opaque,tag\", W/" + tag(product, 0) + ", " + tag(product, 0);
        assertThat(request("GET", path, token, null, list).statusCode()).isEqualTo(200);
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), list).statusCode()).isEqualTo(200);
        assertThat(request("GET", path, token, null, tag(product, 0)).statusCode()).isEqualTo(412);
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), tag(product, 0)).statusCode()).isEqualTo(412);
        assertThat(request("GET", path, token, null, "*").statusCode()).isEqualTo(200);
        assertThat(request("GET", "/inventory/" + UUID.randomUUID(), token, null, "*").statusCode()).isEqualTo(404);
        assertThat(physical(product)).isEqualTo(1);
    }
    @Test
    void missingRevisionWildcardAndBodyVersionHaveExplicitSemantics() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product;
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), null).statusCode()).isEqualTo(428);
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), "*").statusCode()).isEqualTo(428);
        assertThat(request("PATCH", path, token, command(UUID.randomUUID(), "ENTRY", 1, 0), "*").statusCode()).isEqualTo(200);
        assertThat(request("PATCH", path, token, command(UUID.randomUUID(), "ENTRY", 1, 0), null).statusCode()).isEqualTo(409);
        assertThat(request("PATCH", path, token, command(UUID.randomUUID(), "ENTRY", 1, 1), tag(product, 0)).statusCode()).isEqualTo(412);
        assertThat(request("PATCH", path, token, command(UUID.randomUUID(), "ENTRY", 1, 0), tag(product, 1)).statusCode()).isEqualTo(409);
        assertThat(request("PATCH", path, token, command(UUID.randomUUID(), "ENTRY", 1, 1), null).statusCode()).isEqualTo(200);
        assertThat(physical(product)).isEqualTo(2);
    }
    @Test
    void twoClientsReadingSameTagProduceOneWriteAndOnePreconditionFailure() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> write = () -> { start.await(5, TimeUnit.SECONDS); return request("PATCH", "/inventory/" + product, token, patch(UUID.randomUUID()), tag(product, 0)).statusCode(); };
            var first = executor.submit(write); var second = executor.submit(write); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 412);
        }
        assertThat(physical(product)).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=?", Integer.class, product)).isEqualTo(1);
    }
    @Test
    void concurrentSameConditionalRequestReplaysWithoutSecondEffectOrCurrentTag() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); var input = patch(UUID.randomUUID()); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> write = () -> { start.await(5, TimeUnit.SECONDS); return request("PATCH", "/inventory/" + product, token, input, tag(product, 0)); };
            var first = executor.submit(write); var second = executor.submit(write); start.countDown(); var one = first.get(30, TimeUnit.SECONDS); var two = second.get(30, TimeUnit.SECONDS);
            assertThat(one.statusCode()).isEqualTo(200); assertThat(two.statusCode()).isEqualTo(200); assertThat(one.body()).isEqualTo(two.body());
            assertThat(List.of(one.headers().firstValue("ETag").isPresent(), two.headers().firstValue("ETag").isPresent())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(physical(product)).isEqualTo(1);
    }
    @Test
    void staleBatchVersionRevertsEarlierCanonicalItemAndLegacyReplaySurvives() throws Exception {
        var token = token("ADMIN"); var category = category(token); var ids = new ArrayList<>(List.of(product(token, category), product(token, category))); ids.sort(java.util.Comparator.comparing(UUID::toString));
        String path = "/inventory/" + ids.get(1) + "/movements"; var input = command(UUID.randomUUID(), "ENTRY", 1, 0);
        var first = send("POST", path, token, input); assertThat(first.statusCode()).isEqualTo(201);
        var batch = Map.of("requestId", UUID.randomUUID(), "items", List.of(item(ids.get(0), "ENTRY", 3, 0), item(ids.get(1), "ENTRY", 2, 0)));
        assertThat(send("POST", "/inventory/movements/batches", token, batch).statusCode()).isEqualTo(409);
        assertThat(physical(ids.get(0))).isZero(); assertThat(physical(ids.get(1))).isEqualTo(1);
        var replay = send("POST", path, token, input); assertThat(replay.body()).isEqualTo(first.body()); assertThat(replay.headers().firstValue("Idempotent-Replay")).contains("true");
    }
    @ParameterizedTest
    @ValueSource(strings = {"unquoted", "\"unterminated", "*,\"x\"", "\"ok\"garbage", "w/\"lowercase-weak\""})
    void malformedConditionsAreBadRequestsWithoutEffects(String condition) throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token));
        assertThat(request("PATCH", "/inventory/" + product, token, patch(UUID.randomUUID()), condition).statusCode()).isEqualTo(400); assertThat(physical(product)).isZero();
    }
    @Test
    void headerBoundsAndLegacyCommandHeaderRejectionAreEnforced() throws Exception {
        var token = token("ADMIN"); var product = product(token, category(token)); String path = "/inventory/" + product;
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), "\"" + "x".repeat(4096) + "\"").statusCode()).isEqualTo(400);
        assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), String.join(",", java.util.Collections.nCopies(33, "\"x\""))).statusCode()).isEqualTo(400);
        assertThat(request("POST", path + "/movements", token, command(UUID.randomUUID(), "ENTRY", 1, 0), tag(product, 0)).statusCode()).isEqualTo(400);
        var body = Map.of("requestId", UUID.randomUUID(), "items", List.of(item(product, "ENTRY", 1, 0)));
        assertThat(request("POST", "/inventory/movements/batches", token, body, "*").statusCode()).isEqualTo(400); assertThat(physical(product)).isZero();
    }
    @Test
    void metadataChangesPreserveBalanceTagAndConditionalWritesRequireCurrentAuthority() throws Exception {
        var token = token("ADMIN"); var category = category(token); var product = product(token, category); String path = "/inventory/" + product;
        var initial = request("GET", path, token, null, null);
        assertThat(send("PUT", "/products/" + product, token, Map.of("product", productInput(category, "METADATA-ONLY"), "version", 0)).statusCode()).isEqualTo(200);
        var after = request("GET", path, token, null, null); assertThat(after.body()).isEqualTo(initial.body()); assertThat(after.headers().firstValue("ETag")).isEqualTo(initial.headers().firstValue("ETag"));
        assertThat(request("PATCH", path, null, patch(UUID.randomUUID()), tag(product, 0)).statusCode()).isEqualTo(401);
        assertThat(request("PATCH", path, token("OPERATOR"), patch(UUID.randomUUID()), tag(product, 0)).statusCode()).isEqualTo(403);
        revokeWrite(); assertThat(request("PATCH", path, token, patch(UUID.randomUUID()), tag(product, 0)).statusCode()).isEqualTo(403);
        revokeRead(); assertThat(request("GET", path, token, null, tag(product, 0)).statusCode()).isEqualTo(403);
    }
}
