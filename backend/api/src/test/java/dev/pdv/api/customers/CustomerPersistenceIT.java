package dev.pdv.api.customers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import dev.pdv.api.support.PostgresFixture;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerPersistenceIT {
    @Test
    void upgradesV4PreservingCustomerAndEnforcingAuditReferences() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("4").migrate();
            var jdbc = database.jdbc();
            var customer = jdbc.queryForObject("""
                    INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Preserved') RETURNING id
                    """, java.util.UUID.class);
            database.flyway().migrate();
            assertThat(jdbc.queryForObject("SELECT name FROM customers WHERE id=?", String.class, customer)).isEqualTo("Preserved");
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO customer_changes(customer_id,actor_id,action,before_data,after_data)
                    VALUES (?,?,'UPDATED','{}','{}')
                    """, customer, java.util.UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void documentRaceHasOneWinnerAndCanonicalConstraint() throws Exception {
        try (var database = new PostgresFixture()) {
            database.flyway().migrate();
            var jdbc = database.jdbc();
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Integer> insert = () -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Barrier timed out");
                    }
                    try {
                        jdbc.update("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic')");
                        return 1;
                    } catch (DuplicateKeyException expected) {
                        return 0;
                    }
                };
                var first = executor.submit(insert);
                var second = executor.submit(insert);
                start.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO customers(person_type,document,name) VALUES ('PF','123.456.789-09','Synthetic')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void upgradesV3PreservingMarkerAndRebuilds() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("3").migrate();
            database.jdbc().execute("CREATE TABLE preserved_marker(id INTEGER PRIMARY KEY)");
            database.jdbc().update("INSERT INTO preserved_marker VALUES (42)");
            database.flyway().migrate();
            assertThat(database.jdbc().queryForObject("SELECT id FROM preserved_marker", Integer.class)).isEqualTo(42);
            database.close();
            database.flyway().migrate();
            assertThat(database.jdbc().queryForObject("SELECT count(*) FROM customers", Integer.class)).isZero();
        }
    }
}
