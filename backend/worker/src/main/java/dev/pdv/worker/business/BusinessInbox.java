package dev.pdv.worker.business;

import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class BusinessInbox {
    public static final String SIMULATOR = "payment-simulator-v1";
    public static final String COORDINATOR = "sale-coordinator-v1";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public BusinessInbox(JdbcTemplate jdbc, TransactionTemplate transaction) { this.jdbc = jdbc; this.transaction = transaction; }
    public Receipt process(String consumer, BusinessEnvelope envelope) {
        boolean request = SIMULATOR.equals(consumer);
        if ((!request && !COORDINATOR.equals(consumer)) || (request ? !BusinessEnvelope.REQUEST.equals(envelope.type())
                : !BusinessEnvelope.coordinator(envelope.type()))) { throw new BusinessEnvelope.Rejected("Invalid consumer contract"); }
        return transaction.execute(status -> {
            jdbc.execute("SET LOCAL lock_timeout='2s'"); jdbc.execute("SET LOCAL statement_timeout='4s'");
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (r, i) -> 0, "inbox:"+consumer+":"+envelope.eventId());
            var known = jdbc.query("SELECT fingerprint,disposition FROM inbox_messages WHERE consumer=? AND event_id=?", (r, i) -> {
                if (!envelope.fingerprint().equals(r.getString(1))) { throw new BusinessEnvelope.Rejected("Inbox identity conflict"); }
                return new Receipt(true, r.getString(2));
            }, consumer, envelope.eventId());
            if (!known.isEmpty()) { return known.getFirst(); }
            String result = switch(envelope.type()) {
                case BusinessEnvelope.REQUEST -> jdbc.queryForObject("SELECT prepare_business_payment(?)", String.class, envelope.paymentId());
                case BusinessEnvelope.CONFIRM -> jdbc.queryForObject("SELECT confirm_business_sale(?)", String.class, envelope.paymentId());
                case BusinessEnvelope.RELEASE -> jdbc.queryForObject("SELECT release_business_sale(?)", String.class, envelope.paymentId());
                case BusinessEnvelope.RECONCILED -> jdbc.queryForObject("SELECT apply_business_reconciliation(?,?)", String.class, envelope.paymentId(), envelope.outcome());
                case BusinessEnvelope.RESULT -> jdbc.queryForObject("SELECT apply_business_payment_result(?,?)", String.class, envelope.paymentId(), envelope.outcome());
                default -> throw new BusinessEnvelope.Rejected("Unknown business step");
            };
            if ("BUSY".equals(result) || "NOT_STARTED".equals(result)) { throw new TransientDataAccessResourceException("Saga temporarily unavailable"); }
            if (result==null || "NOT_FOUND".equals(result)) { throw new BusinessEnvelope.Rejected("Unknown payment operation"); }
            jdbc.update("INSERT INTO inbox_messages(consumer,event_id,payment_id,fingerprint,disposition) VALUES (?,?,?,?,?)", consumer, envelope.eventId(), envelope.paymentId(), envelope.fingerprint(), result);
            return new Receipt(false, result);
        });
    }
    public record Receipt(boolean replayed, String disposition) { }
    public void observe(OperationTrace.Span span, String outcome, long started) { OperationTrace.record(jdbc,span,"business_consume",outcome,started); }
}
