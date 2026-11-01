package dev.pdv.api.inventory;

import dev.pdv.api.support.PostgresFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryBalancePersistenceIT {
    @Test
    void upgradesV9PreservingProductAndBackfillingOnlyAuditedZeroThenRebuilds() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("9").migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Preserved') RETURNING id", UUID.class);
            UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'PRESERVED','Preserved',12.34,0,'UN',0) RETURNING id", UUID.class, category);
            database.flyway().migrate();
            assertThat(jdbc.queryForObject("SELECT sale_price FROM products WHERE id=?", java.math.BigDecimal.class, product)).isEqualByComparingTo("12.34");
            assertThat(jdbc.queryForObject("SELECT physical+reserved+version FROM inventory_balances WHERE product_id=?", Long.class, product)).isZero();
            assertThat(jdbc.queryForObject("SELECT source FROM inventory_openings WHERE product_id=?", String.class, product)).isEqualTo("ZERO_INITIALIZATION");
            database.close(); database.flyway().migrate(); assertThat(database.jdbc().queryForObject("SELECT count(*) FROM inventory_balances", Integer.class)).isZero();
        }
    }
    @Test
    void databaseRejectsInvalidBalancesReferencesAndOpeningChangesAndInitializesDirectInserts() throws Exception {
        try (var database = new PostgresFixture()) {
            database.flyway().migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
            UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'DIRECT','Synthetic',0,0,'UN',0) RETURNING id", UUID.class, category);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_openings WHERE product_id=?", Integer.class, product)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balances SET physical=-1 WHERE product_id=?", product)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balances SET reserved=-1 WHERE product_id=?", product)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balances SET reserved=1 WHERE product_id=?", product)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO inventory_balances(product_id) VALUES (?)", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_openings WHERE product_id=?", product)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE inventory_openings SET physical=0 WHERE product_id=?", product)).isInstanceOf(DataAccessException.class);
            jdbc.update("UPDATE inventory_balances SET physical=?,reserved=? WHERE product_id=?", Long.MAX_VALUE, Long.MAX_VALUE - 1, product);
            assertThat(jdbc.queryForObject("SELECT physical-reserved FROM inventory_balances WHERE product_id=?", Long.class, product)).isEqualTo(1);
        }
    }
}
