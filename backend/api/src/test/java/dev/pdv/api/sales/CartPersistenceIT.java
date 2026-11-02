package dev.pdv.api.sales;

import dev.pdv.api.support.PostgresFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartPersistenceIT {
    @Test void upgradesV12PreservingStockAndReservationContractThenRebuilds() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("12").migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
            UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'PRESERVED','Synthetic',0,0,'UN',0) RETURNING id", UUID.class, category);
            jdbc.update("UPDATE inventory_balances SET physical=9,reserved=2,version=4 WHERE product_id=?", product);
            assertThat(database.flyway().migrate().migrationsExecuted).isEqualTo(dev.pdv.api.support.MigrationBaseline.COUNT - 12);
            assertThat(jdbc.queryForObject("SELECT physical-reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT protect_inventory_reservation(?)", Boolean.class, UUID.randomUUID())).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM carts", Integer.class)).isZero();
            assertThat(database.flyway().migrate().migrationsExecuted).isZero(); database.close();
            assertThat(database.flyway().migrate().migrationsExecuted).isEqualTo(dev.pdv.api.support.MigrationBaseline.COUNT);
            assertThat(database.jdbc().queryForObject("SELECT count(*) FROM cart_changes", Integer.class)).isZero();
        }
    }
}
