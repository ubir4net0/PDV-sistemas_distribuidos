package dev.pdv.worker.inventory;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Each reservation is an independent, bounded database transaction. */
public class ReservationExpiry {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationExpiry.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final int batchSize;
    public ReservationExpiry(JdbcTemplate jdbc, TransactionTemplate transaction, int batchSize) {
        if (batchSize < 1 || batchSize > 100) { throw new IllegalArgumentException("Expiry batch must be between 1 and 100"); }
        this.jdbc = jdbc; this.transaction = transaction; this.batchSize = batchSize;
    }
    @Scheduled(fixedDelayString = "${pdv.reservation-expiry.interval-ms:5000}", initialDelayString = "${pdv.reservation-expiry.interval-ms:5000}")
    public void scheduled() {
        try {
            var result = runRound();
            if (result.expired() > 0 || result.failed() > 0) { LOG.info("reservation_expiry scanned={} expired={} failed={}", result.scanned(), result.expired(), result.failed()); }
        } catch (DataAccessException exception) { LOG.warn("Reservation expiry unavailable; retry next round"); }
    }
    public Round runRound() {
        var ids = jdbc.queryForList("""
                SELECT id FROM inventory_reservations WHERE status='ACTIVE' AND NOT payment_protected
                AND expires_at<=CURRENT_TIMESTAMP ORDER BY expires_at,id LIMIT ?
                """, UUID.class, batchSize);
        int expired = 0; int failed = 0;
        for (var id : ids) {
            try {
                boolean applied = Boolean.TRUE.equals(transaction.execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout='2s'"); jdbc.execute("SET LOCAL statement_timeout='4s'");
                    return jdbc.queryForObject("SELECT expire_inventory_reservation(?)", Boolean.class, id);
                }));
                if (applied) { expired++; }
            } catch (DataAccessException exception) { failed++; LOG.warn("Reservation expiry transaction failed; retry next round"); }
        }
        return new Round(ids.size(), expired, failed);
    }
    public record Round(int scanned, int expired, int failed) { }
}
