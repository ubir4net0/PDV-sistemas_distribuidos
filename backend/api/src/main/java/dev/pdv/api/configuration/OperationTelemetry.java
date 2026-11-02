package dev.pdv.api.configuration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Explicit allowlisted spans, recorded outside the business transaction. */
@Service
public class OperationTelemetry {
    private final JdbcTemplate jdbc;
    public OperationTelemetry(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public String checkout(UUID payment, boolean replay, long started) {
        String trace=payment.toString().replace("-",""); String root=trace.substring(0,16); String span=replay ? UUID.randomUUID().toString().replace("-","").substring(0,16) : root;
        try { String json=jdbc.queryForObject("SELECT record_operation_telemetry('api',?,?,?,?,?,?)::text",String.class,
                replay ? "api_checkout_replay" : "api_checkout",trace,span,replay ? root : null,replay ? "REPLAY" : "OK",Math.max(0,(System.nanoTime()-started)/1000000));
            LoggerFactory.getLogger("pdv.telemetry").info("{}",json);
        } catch(org.springframework.dao.DataAccessException failure) { LoggerFactory.getLogger("pdv.telemetry").warn("{\"service\":\"api\",\"event\":\"telemetry_unavailable\"}"); }
        return "00-"+trace+"-"+span+"-01";
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('ADMIN')")
    public Dashboard dashboard(String trace) {
        if(trace!=null && (!trace.matches("[a-f0-9]{32}") || trace.equals("0".repeat(32)))) { throw new ApiFailure(400,"invalid_request"); }
        String filter=" WHERE created_at>=clock_timestamp()-interval '24 hours'"+(trace==null ? "" : " AND trace_id=?");
        Object[] args=trace==null ? new Object[0] : new Object[]{trace};
        var logs=jdbc.query("SELECT * FROM operation_telemetry"+filter+" ORDER BY created_at DESC,id DESC LIMIT 100",(r,i)->new Entry(r.getObject("id",UUID.class),r.getString("service"),r.getString("event"),r.getString("trace_id"),r.getString("span_id"),r.getString("parent_span_id"),r.getString("outcome"),r.getLong("duration_ms"),r.getTimestamp("created_at").toInstant()),args);
        var metrics=jdbc.query("SELECT service,event,count(*),count(*) FILTER(WHERE outcome IN ('UNCONFIRMED','REJECTED')),floor(avg(duration_ms)),percentile_disc(0.95) WITHIN GROUP(ORDER BY duration_ms) FROM operation_telemetry"+filter+" GROUP BY service,event ORDER BY service,event",(r,i)->new Metric(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5),r.getLong(6)),args);
        return new Dashboard(Instant.now(),metrics,logs);
    }
    public record Entry(UUID id,String service,String event,String traceId,String spanId,String parentSpanId,String outcome,long durationMs,Instant createdAt) { }
    public record Metric(String service,String event,long count,long failures,long averageMs,long p95Ms) { }
    public record Dashboard(Instant generatedAt,List<Metric> metrics,List<Entry> logs) { }
}
