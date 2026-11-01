package dev.pdv.api.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Teardown only, in an explicitly owned test database; never packaged in the application. */
public final class InventoryFixtureCleanup {
    private InventoryFixtureCleanup() { }
    public static void clean(JdbcTemplate jdbc, List<UUID> products, List<UUID> actors) {
        if (products.isEmpty()) { return; }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database = connection.getCatalog();
            if (!(database.matches("pdv_verify_[a-f0-9]{32}") || ("pdv_ci".equals(database) && "true".equals(System.getenv("CI"))))) {
                throw new IllegalStateException("Fixture cleanup is limited to owned Verify/CI databases");
            }
            if (!connection.getAutoCommit()) { throw new IllegalStateException("Fixture cleanup requires its own transaction"); }
            try {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) { statement.execute("SET LOCAL session_replication_role = replica"); }
                delete(connection, "DELETE FROM inventory_openings WHERE product_id=ANY(?)", products);
                delete(connection, "DELETE FROM inventory_balances WHERE product_id=ANY(?)", products);
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
            return null;
        });
    }
    private static void delete(Connection connection, String sql, List<UUID> ids) throws SQLException {
        var array = connection.createArrayOf("uuid", ids.toArray());
        try (var statement = connection.prepareStatement(sql)) { statement.setArray(1, array); statement.executeUpdate(); }
        finally { array.free(); }
    }
}
