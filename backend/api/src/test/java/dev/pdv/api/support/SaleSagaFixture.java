package dev.pdv.api.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic aggregates in a private UUID schema on real PostgreSQL. */
public final class SaleSagaFixture implements AutoCloseable {
    public final PostgresFixture database = new PostgresFixture();
    public final JdbcTemplate jdbc;
    private final UUID actor;
    private final UUID customer;
    private final UUID category;
    public SaleSagaFixture(String version) {
        database.configured(version).migrate(); jdbc = database.jdbc();
        actor = jdbc.queryForObject("INSERT INTO users(name,email,password_hash) VALUES ('Synthetic','saga@example.test',?) RETURNING id", UUID.class, "$2a$04$" + "A".repeat(53));
        customer = jdbc.queryForObject("INSERT INTO customers(person_type,document,name) VALUES ('PF','12345678909','Synthetic') RETURNING id", UUID.class);
        category = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Synthetic') RETURNING id", UUID.class);
    }
    public Operation operation(String scenario, boolean due) {
        UUID product = jdbc.queryForObject("INSERT INTO products(category_id,sku,name,sale_price,cost,unit,minimum_stock,status) VALUES (?,?,'Synthetic',12.34,0,'UN',0,'ATIVO') RETURNING id", UUID.class, category, UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
        jdbc.update("UPDATE inventory_balances SET physical=10,reserved=2,version=1 WHERE product_id=?", product);
        UUID cart = UUID.randomUUID(); UUID reservation = UUID.randomUUID(); UUID sale = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        jdbc.update("INSERT INTO carts(id,actor_id,customer_id,created_at,updated_at) VALUES (?,?,?,clock_timestamp(),clock_timestamp())", cart, actor, customer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity,unit_price) VALUES (?,?,?,2,12.34)", UUID.randomUUID(), cart, product);
        jdbc.update("INSERT INTO inventory_reservations(id,actor_id,created_at,expires_at) VALUES (?,?,clock_timestamp()-interval '2 seconds',clock_timestamp()+(? * interval '1 second'))", reservation, actor, due ? -1 : 900);
        jdbc.update("INSERT INTO inventory_reservation_items(reservation_id,product_id,quantity) VALUES (?,?,2)", reservation, product);
        jdbc.query("SELECT record_inventory_reservation_event(?,'CREATED')", (r, i) -> 0, reservation);
        jdbc.update("INSERT INTO sales(id,cart_id,actor_id,customer_id,reservation_id,total) VALUES (?,?,?,?,?,24.68)", sale, cart, actor, customer, reservation);
        jdbc.update("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price) VALUES (?,?,2,12.34)", sale, product);
        jdbc.update("INSERT INTO payments(id,sale_id,method,scenario,amount) VALUES (?,?,'PIX',?,24.68)", payment, sale, scenario);
        jdbc.update("INSERT INTO payment_intents(payment_id,payload) SELECT id,jsonb_build_object('version',1,'paymentId',id,'saleId',sale_id,'method',method,'scenario',scenario,'amount',amount) FROM payments WHERE id=?", payment);
        jdbc.update("INSERT INTO sale_events(sale_id,version,kind,snapshot) VALUES (?,0,'CHECKOUT','{}')", sale);
        return new Operation(sale, payment, reservation, product);
    }
    public String start(UUID id) { return jdbc.queryForObject("SELECT start_sale_payment(?)", String.class, id); }
    public String result(UUID id, String outcome) { return jdbc.queryForObject("SELECT record_simulated_payment(?,?)", String.class, id, outcome); }
    public long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    @Override public void close() throws Exception { database.close(); }
    public record Operation(UUID sale, UUID payment, UUID reservation, UUID product) { }
}
