package dev.pdv.worker.business;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

public class BusinessOutbox {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessOutbox.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Sender sender;
    private final JsonMapper json;
    private final int batchSize;
    public BusinessOutbox(JdbcTemplate jdbc, TransactionTemplate transaction, Sender sender, JsonMapper json, int batchSize) {
        if (batchSize<1 || batchSize>100) { throw new IllegalArgumentException("Outbox batch must be between 1 and 100"); }
        this.jdbc = jdbc; this.transaction = transaction; this.sender = sender; this.json = json; this.batchSize = batchSize;
    }
    @Scheduled(fixedDelayString = "${pdv.business.interval-ms:1000}", initialDelayString = "${pdv.business.interval-ms:1000}")
    public void scheduled() {
        try { var r = runRound(); if (r.published()>0 || r.failed()>0) { LOG.info("business_outbox scanned={} published={} failed={}", r.scanned(), r.published(), r.failed()); } }
        catch (DataAccessException failure) { LOG.warn("Business outbox unavailable; retry next round"); }
    }
    public Round runRound() {
        var ids = jdbc.queryForList("SELECT event_id FROM outbox_deliveries WHERE status='PENDING' AND attempts<5 AND next_attempt_at<=clock_timestamp() AND version<9223372036854775807 ORDER BY next_attempt_at,event_id LIMIT ?", UUID.class, batchSize);
        int published = 0; int failed = 0;
        for (var id : ids) {
            try {
                var outcome = transaction.execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout='2s'"); jdbc.execute("SET LOCAL statement_timeout='4s'");
                    var rows = jdbc.query("""
                            SELECT e.payload::text FROM outbox_deliveries d JOIN outbox_events e ON e.id=d.event_id
                            WHERE d.event_id=? AND d.status='PENDING' AND d.attempts<5 AND d.next_attempt_at<=clock_timestamp() AND d.version<9223372036854775807 FOR UPDATE OF d SKIP LOCKED
                            """, (r, i) -> json.readValue(r.getString(1), BusinessEnvelope.class), id);
                    if (rows.isEmpty()) { return "SKIPPED"; }
                    try { sender.send(rows.getFirst()); }
                    catch (AmqpException failure) {
                        jdbc.update("""
                                UPDATE outbox_deliveries SET attempts=attempts+1,status=CASE WHEN attempts+1>=5 THEN 'DEAD' ELSE 'PENDING' END,
                                next_attempt_at=clock_timestamp()+(LEAST(60,power(2,attempts)::integer)*interval '1 second'),last_error='UNCONFIRMED',version=version+1,updated_at=clock_timestamp() WHERE event_id=?
                                """, id); return "FAILED";
                    }
                    jdbc.update("UPDATE outbox_deliveries SET status='PUBLISHED',attempts=attempts+1,last_error=NULL,version=version+1,updated_at=clock_timestamp() WHERE event_id=?", id); return "PUBLISHED";
                });
                if ("PUBLISHED".equals(outcome)) { published++; } else if ("FAILED".equals(outcome)) { failed++; }
            } catch (DataAccessException failure) { failed++; LOG.warn("Business outbox transaction failed; publication may be repeated"); }
        }
        return new Round(ids.size(), published, failed);
    }
    @FunctionalInterface public interface Sender { void send(BusinessEnvelope envelope); }
    public record Round(int scanned, int published, int failed) { }
}
