package dev.pdv.worker.payment;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Local polling adapter for the academic simulator; reliable AMQP transport belongs to PDV-038. */
public class PaymentSimulation {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentSimulation.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final int batchSize;
    public PaymentSimulation(JdbcTemplate jdbc, TransactionTemplate transaction, int batchSize) {
        if (batchSize<1 || batchSize>100) { throw new IllegalArgumentException("Simulation batch must be between 1 and 100"); }
        this.jdbc = jdbc; this.transaction = transaction; this.batchSize = batchSize;
    }
    @Scheduled(fixedDelayString = "${pdv.simulated-payments.interval-ms:5000}", initialDelayString = "${pdv.simulated-payments.interval-ms:5000}")
    public void scheduled() {
        try {
            var result = runRound();
            if (result.completed()>0 || result.failed()>0) { LOG.info("simulated_payment scanned={} completed={} skipped={} failed={}", result.scanned(), result.completed(), result.skipped(), result.failed()); }
        } catch (DataAccessException exception) { LOG.warn("Simulated payments unavailable; retry next round"); }
    }
    public Round runRound() {
        var pending = jdbc.query("""
                SELECT p.id,p.scenario FROM payments p JOIN payment_intents i ON i.payment_id=p.id JOIN sales s ON s.id=p.sale_id
                JOIN inventory_reservations r ON r.id=s.reservation_id
                WHERE r.status='ACTIVE' AND ((p.status='CRIADO' AND r.expires_at>clock_timestamp()) OR (p.status='EM_PROCESSAMENTO' AND r.payment_protected))
                ORDER BY CASE WHEN p.status='EM_PROCESSAMENTO' THEN 0 ELSE 1 END,p.created_at,p.id LIMIT ?
                """, (r, i) -> new Pending(r.getObject(1, UUID.class), r.getString(2)), batchSize);
        int completed = 0; int skipped = 0; int failed = 0;
        for (var p : pending) {
            try {
                String started = command("SELECT start_sale_payment(?)", p.id());
                if (!"EM_PROCESSAMENTO".equals(started)) { skipped++; continue; }
                String expected = outcome(p.scenario());
                if (expected.equals(command("SELECT record_simulated_payment(?,?)", p.id(), expected))) { completed++; } else { skipped++; }
            } catch (DataAccessException exception) { failed++; LOG.warn("Simulated payment transaction failed; retry next round"); }
        }
        return new Round(pending.size(), completed, skipped, failed);
    }
    private String command(String sql, Object... args) {
        return transaction.execute(status -> {
            jdbc.execute("SET LOCAL lock_timeout='2s'"); jdbc.execute("SET LOCAL statement_timeout='4s'"); return jdbc.queryForObject(sql, String.class, args);
        });
    }
    public static String outcome(String scenario) {
        return switch (scenario) { case "APROVADO" -> "APROVADO"; case "RECUSADO" -> "RECUSADO"; case "TIMEOUT" -> "INDETERMINADO"; default -> throw new IllegalArgumentException("Unknown simulation scenario"); };
    }
    private record Pending(UUID id, String scenario) { }
    public record Round(int scanned, int completed, int skipped, int failed) { }
}
