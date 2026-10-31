package dev.pdv.api.customers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import dev.pdv.api.support.PostgresFixture;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressPersistenceIT {
    @Test
    void databasePartialUniqueIndexAndForeignKeyProtectConcurrentInserts() throws Exception {
        try (var database = new PostgresFixture()) {
            database.flyway().migrate();
            var jdbc = database.jdbc();
            UUID customer = jdbc.queryForObject("""
                    INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic') RETURNING id
                    """, UUID.class);
            String insert = """
                    INSERT INTO customer_addresses(customer_id,address_type,street,house_number,neighborhood,city,state,postal_code,is_primary)
                    VALUES (?,'Teste','Synthetic','1','Centro','Manaus','AM','69000000',true)
                    """;
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Integer> writer = () -> {
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Barrier timed out"); }
                    try { jdbc.update(insert, customer); return 1; }
                    catch (DuplicateKeyException expected) { return 0; }
                };
                var first = executor.submit(writer);
                var second = executor.submit(writer);
                start.countDown();
                assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            }
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE is_primary", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void upgradeV5PreservesCustomerAndAuditThenRebuildsAllSchemas() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("5").migrate();
            var jdbc = database.jdbc();
            UUID actor = jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic','test@example.test',?) RETURNING id",
                    UUID.class, new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString()));
            UUID customer = jdbc.queryForObject("""
                    INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Preserved') RETURNING id
                    """, UUID.class);
            jdbc.update("""
                    INSERT INTO customer_changes(customer_id,actor_id,action,before_data,after_data) VALUES (?,?,'UPDATED','{}','{}')
                    """, customer, actor);
            database.flyway().migrate();
            assertThat(jdbc.queryForObject("SELECT name FROM customers WHERE id=?", String.class, customer)).isEqualTo("Preserved");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_changes", Integer.class)).isEqualTo(1);
            database.close();
            database.flyway().migrate();
            assertThat(database.jdbc().queryForObject("SELECT count(*) FROM customer_addresses", Integer.class)).isZero();
        }
    }
}
