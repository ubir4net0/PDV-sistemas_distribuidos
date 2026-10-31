package dev.pdv.api.catalog;

import dev.pdv.api.support.PostgresFixture;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogPersistenceIT {
    @Test
    void upgradesV6PreservingCustomerThenRebuildsAndReplays() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("6").migrate(); var jdbc = database.jdbc();
            UUID customer = jdbc.queryForObject("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Preserved') RETURNING id", UUID.class);
            database.flyway().migrate();
            assertThat(jdbc.queryForObject("SELECT name FROM customers WHERE id=?", String.class, customer)).isEqualTo("Preserved");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isZero();
            database.close(); database.flyway().migrate();
            assertThat(database.jdbc().queryForObject("SELECT count(*) FROM categories", Integer.class)).isZero();
        }
    }
    @Test
    void databaseEnforcesSkuRaceForeignKeysAndNonnegativeAmounts() throws Exception {
        try (var database = new PostgresFixture()) {
            database.flyway().migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
            String sql = "INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'RACE','Synthetic',12.34,0,'UN',0)";
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Integer> insert = () -> {
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Barrier timed out"); }
                    try { jdbc.update(sql, category); return 1; } catch (DuplicateKeyException expected) { return 0; }
                };
                var first = executor.submit(insert); var second = executor.submit(insert); start.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            }
            assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id=?", category)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE products SET sale_price=-1")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE products SET minimum_stock=-1")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE products SET category_id=?", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE products SET status='UNKNOWN'")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
