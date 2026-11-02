package dev.pdv.api.sales;

import dev.pdv.api.support.MigrationBaseline;
import dev.pdv.api.support.PostgresFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutPersistenceIT {
    @Test void upgradesV13WithExistingCartThenRebuildsAndReplays() throws Exception {
        try (var database = new PostgresFixture()) {
            database.configured("13").migrate(); var jdbc = database.jdbc();
            UUID actor = jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic','synthetic@example.test',?) RETURNING id", UUID.class, "$2a$04$" + "A".repeat(53));
            UUID customer = jdbc.queryForObject("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic') RETURNING id", UUID.class);
            UUID cart = UUID.randomUUID(); jdbc.update("INSERT INTO carts(id,actor_id,customer_id,created_at,updated_at,version) VALUES (?,?,?,clock_timestamp(),clock_timestamp(),5)", cart, actor, customer);
            assertThat(database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT-13);
            assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE id=?", Long.class, cart)).isEqualTo(5);
            jdbc.update("UPDATE carts SET version=6 WHERE id=?", cart);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sales", Integer.class)).isZero();
            assertThat(database.flyway().migrate().migrationsExecuted).isZero(); database.close();
            assertThat(database.flyway().migrate().migrationsExecuted).isEqualTo(MigrationBaseline.COUNT);
            assertThat(database.jdbc().queryForObject("SELECT count(*) FROM checkout_operations", Integer.class)).isZero();
        }
    }
}
