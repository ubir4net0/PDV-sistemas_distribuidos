package dev.pdv.api.purchasing;

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

class SupplierPersistenceIT {
    @Test
    void upgradesV8PreservingUnlinkedProductsAndRebuilds() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("8").migrate(); var jdbc = database.jdbc();
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Preserved') RETURNING id", UUID.class);
            jdbc.update("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,'PRESERVED','Preserved',1.23,0,'UN',0)", category);
            database.flyway().migrate();
            assertThat(jdbc.queryForObject("SELECT supplier_id FROM products", UUID.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT sale_price FROM products", java.math.BigDecimal.class)).isEqualByComparingTo("1.23");
            assertThatThrownBy(() -> jdbc.update("UPDATE products SET supplier_id=?", UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
            database.close(); database.flyway().migrate(); assertThat(database.jdbc().queryForObject("SELECT count(*) FROM suppliers", Integer.class)).isZero();
        }
    }
    @Test
    void databaseEnforcesConcurrentCanonicalCnpjAndRestrictsLinkedSupplierDeletion() throws Exception {
        try (var database = new PostgresFixture()) {
            database.flyway().migrate(); var jdbc = database.jdbc(); var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Integer> insert = () -> {
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Barrier timed out"); }
                    try { jdbc.update("INSERT INTO suppliers(cnpj,legal_name) VALUES ('11222333000181','Race')"); return 1; } catch (DuplicateKeyException expected) { return 0; }
                };
                var first = executor.submit(insert); var second = executor.submit(insert); start.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            }
            UUID supplier = jdbc.queryForObject("SELECT id FROM suppliers", UUID.class);
            UUID category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
            jdbc.update("INSERT INTO products(category_id,supplier_id,sku,name,sale_price,cost,unit,minimum_stock) VALUES (?,?,'LINKED','Synthetic',0,0,'UN',0)", category, supplier);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM suppliers WHERE id=?", supplier)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE suppliers SET cnpj='12.abc.345/01de-35'")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
