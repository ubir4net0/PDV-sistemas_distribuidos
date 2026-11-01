package dev.pdv.api.inventory;

import dev.pdv.api.support.PostgresFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMovementPersistenceIT {
    @Test
    void upgradesV10PreservingBalanceAndOpeningThenRebuilds() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("10").migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
            UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'PRESERVED','Synthetic',0,0,'UN',0) RETURNING id", UUID.class, category);
            jdbc.update("UPDATE inventory_balances SET physical=9,reserved=2,version=4 WHERE product_id=?", product);
            database.flyway().migrate(); assertThat(jdbc.queryForObject("SELECT physical-reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_openings WHERE product_id=?", Integer.class, product)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements", Integer.class)).isZero();
            database.close(); database.flyway().migrate(); assertThat(database.jdbc().queryForObject("SELECT count(*) FROM inventory_operations", Integer.class)).isZero();
        }
    }
}
