package dev.pdv.api.catalog;

import dev.pdv.api.support.PostgresFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogQueryPersistenceIT {
    @Test
    void upgradesV7WithoutChangingProductsAndReplaysIndexes() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("7").migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Preserved') RETURNING id", UUID.class);
            jdbc.update("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'PRESERVED','Preserved',1.23,0,'UN',0)", category);
            database.flyway().migrate();
            assertThat(jdbc.queryForObject("SELECT sale_price FROM products", java.math.BigDecimal.class)).isEqualByComparingTo("1.23");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND indexname='products_name_prefix_idx'", Integer.class)).isEqualTo(1);
            database.close(); database.flyway().migrate();
            assertThat(database.jdbc().queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND indexname='products_status_page_idx'", Integer.class)).isEqualTo(1);
        }
    }
}
