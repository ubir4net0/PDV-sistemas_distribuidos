package dev.pdv.api.catalog;

import dev.pdv.api.support.CatalogHttpSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogQueryHttpIT extends CatalogHttpSupport {
    @Test
    void searchesLiteralPrefixesOnNameOrSkuAndCombinesFiltersAndPages() throws Exception {
        var admin = token("ADMIN"); var operator = token("OPERATOR"); var category = category(admin); var other = category(admin);
        var first = product(admin, category); var second = product(admin, category); product(admin, other);
        jdbc.update("UPDATE products SET name='Literal%_!A',sku='UNIQUE-ONE' WHERE id=?", first);
        jdbc.update("UPDATE products SET name='LiteralXXA',sku='UNIQUE-TWO',status='INATIVO' WHERE id=?", second);
        var literal = json.readTree(send("GET", "/products?q=literal%25_%21&categoryId=" + category, operator, null).body());
        assertThat(literal.get("total").asLong()).isEqualTo(1);
        assertThat(literal.get("items").get(0).get("id").asString()).isEqualTo(first.toString());
        assertThat(json.readTree(send("GET", "/products?q=unique-&status=INATIVO", operator, null).body()).get("total").asLong()).isEqualTo(1);
        var page = json.readTree(send("GET", "/products?categoryId=" + category + "&size=1&page=0", operator, null).body());
        var next = json.readTree(send("GET", "/products?categoryId=" + category + "&size=1&page=1", operator, null).body());
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("id").asString()).isNotEqualTo(next.get("items").get(0).get("id").asString());
        assertThat(json.readTree(send("GET", "/products?categoryId=" + category + "&page=9", operator, null).body()).get("items").size()).isZero();
        assertThat(json.readTree(send("GET", "/products?q=absent", operator, null).body()).get("total").asLong()).isZero();
    }
    @Test
    void stableIdTieBreakAndCategoryActiveFilterAreApplied() throws Exception {
        var token = token("ADMIN"); var category = category(token); var other = category(token);
        product(token, category); product(token, category);
        var first = send("GET", "/products?size=1", token, null).body();
        assertThat(send("GET", "/products?size=1", token, null).body()).isEqualTo(first);
        send("PATCH", "/categories/" + other + "/status", token, Map.of("active", false, "version", 0));
        var result = json.readTree(send("GET", "/categories?active=false&size=1", token("OPERATOR"), null).body());
        assertThat(result.get("total").asLong()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("id").asString()).isEqualTo(other.toString());
    }
    @Test
    void collectionReadsRequireRoleAndCurrentReadPermission() throws Exception {
        for (String path : List.of("/categories", "/products")) {
            assertThat(send("GET", path, null, null).statusCode()).isEqualTo(401);
            assertThat(send("GET", path, token("OPERATOR"), null).statusCode()).isEqualTo(200);
        }
        String token = token("ADMIN");
        jdbc.update("DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE resource='catalog' AND action='read')");
        for (String path : List.of("/categories", "/products")) { assertThat(send("GET", path, token, null).statusCode()).isEqualTo(403); }
    }
    @ParameterizedTest
    @ValueSource(strings = {"/products?page=-1", "/products?size=101", "/products?page=2147483647&size=100", "/products?status=UNKNOWN",
            "/products?categoryId=bad", "/categories?active=bad", "/categories?size=0"})
    void invalidFiltersAreBadRequests(String path) throws Exception { assertThat(send("GET", path, token("ADMIN"), null).statusCode()).isEqualTo(400); }
}
