package dev.pdv.api.catalog;

import dev.pdv.api.support.CatalogHttpSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Closed-loop local workload confirmed by the owner, not a production capacity claim. */
class CatalogLoadIT extends CatalogHttpSupport {
    @Test
    void measuresAuthenticatedCatalogWithTenConcurrentClientsAndTenThousandProducts() throws Exception {
        String token = token("OPERATOR"); var category = category(token("ADMIN"));
        try {
            // Synthetic fixture only: no real business state transitions or customer data.
            jdbc.update("""
                    INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock,status)
                    SELECT ?, 'LOAD-'||lpad(i::text,5,'0'), 'Product'||lpad(i::text,5,'0'),12.34,4.56,'UN',0,
                        CASE WHEN i%4=0 THEN 'INATIVO' ELSE 'ATIVO' END FROM generate_series(1,10000) AS i
                    """, category);
            jdbc.execute("ANALYZE products");
            var namePlan = jdbc.queryForList("EXPLAIN (ANALYZE,BUFFERS) SELECT id FROM products WHERE lower(name) LIKE 'product009%'", String.class);
            var skuPlan = jdbc.queryForList("EXPLAIN (ANALYZE,BUFFERS) SELECT id FROM products WHERE sku LIKE 'LOAD-009%'", String.class);
            assertThat(String.join("\n", namePlan)).contains("products_name_prefix_idx");
            assertThat(String.join("\n", skuPlan)).contains("products_sku_prefix_idx");
            String[] paths = {"/products?q=Product009&size=20", "/products?categoryId=" + category + "&status=ATIVO&size=20",
                    "/products?page=25&size=20", "/products?q=LOAD-009&categoryId=" + category + "&status=ATIVO&size=10"};
            phase(token, paths, 10);
            var samples = phase(token, paths, 60);
            var durations = samples.stream().map(Sample::millis).sorted().toList();
            long errors = samples.stream().filter(sample -> !sample.success()).count();
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("workload", Map.of("products", 10000, "concurrentClients", 10, "warmupSeconds", 10, "measurementSeconds", 60,
                    "mode", "closed-loop; four paths round-robin per client; shared authenticated operator"));
            report.put("environment", Map.of("java", System.getProperty("java.version"), "os", System.getProperty("os.name"),
                    "arch", System.getProperty("os.arch"), "processors", Runtime.getRuntime().availableProcessors(),
                    "postgres", jdbc.queryForObject("SELECT version()", String.class), "ci", "true".equals(System.getenv("CI"))));
            report.put("requests", durations.size()); report.put("errors", errors); report.put("requestsPerSecond", durations.size() / 60.0);
            report.put("p50Millis", percentile(durations, .50)); report.put("p95Millis", percentile(durations, .95)); report.put("p99Millis", percentile(durations, .99));
            var perPath = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < paths.length; i++) {
                int route = i; var routeTimes = samples.stream().filter(sample -> sample.route() == route).map(Sample::millis).sorted().toList();
                perPath.add(Map.of("path", paths[i].replace(category.toString(), "SYNTHETIC_CATEGORY"), "requests", routeTimes.size(), "p95Millis", percentile(routeTimes, .95)));
            }
            report.put("perPath", perPath); report.put("namePlan", namePlan); report.put("skuPlan", skuPlan);
            Files.createDirectories(Path.of("target")); Files.writeString(Path.of("target/catalog-load.json"), json.writeValueAsString(report));
            System.out.println("CATALOG_LOAD " + json.writeValueAsString(report));
            assertThat(durations).isNotEmpty(); assertThat(errors).isZero(); assertThat(percentile(durations, .95)).isLessThan(200.0);
        } finally { jdbc.update("DELETE FROM products WHERE category_id=?", category); }
    }
    private List<Sample> phase(String token, String[] paths, int seconds) throws Exception {
        var samples = new ConcurrentLinkedQueue<Sample>(); var start = new CountDownLatch(1); var deadline = new AtomicLong();
        try (var executor = Executors.newFixedThreadPool(10)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 10; worker++) {
                int initial = worker;
                tasks.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Load barrier timed out"); }
                    int i = initial;
                    while (System.nanoTime() < deadline.get()) {
                        int route = i++ % paths.length; long before = System.nanoTime(); boolean success;
                        try { var response = send("GET", paths[route], token, null); success = response.statusCode() == 200 && json.readTree(response.body()).has("items"); }
                        catch (Exception exception) { success = false; }
                        samples.add(new Sample(route, Duration.ofNanos(System.nanoTime() - before).toNanos() / 1_000_000.0, success));
                    }
                    return null;
                }));
            }
            deadline.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)); start.countDown();
            for (var task : tasks) { task.get(seconds + 25L, TimeUnit.SECONDS); }
        }
        return List.copyOf(samples);
    }
    private static double percentile(List<Double> sorted, double quantile) {
        if (sorted.isEmpty()) { return Double.POSITIVE_INFINITY; }
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1));
    }
    private record Sample(int route, double millis, boolean success) { }
}
