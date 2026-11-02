package dev.pdv.worker.business;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/** Only operation identities and fixed outcomes enter telemetry. */
public final class OperationTrace {
    private static final ThreadLocal<Span> CURRENT=new ThreadLocal<>();
    private OperationTrace() { }
    public static Span child(UUID payment, Object header) {
        String trace=payment.toString().replace("-",""); String parent=trace.substring(0,16);
        if(header instanceof String value && value.matches("00-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}")
                && value.substring(3,35).equals(trace) && !value.substring(36,52).equals("0".repeat(16))) { parent=value.substring(36,52); }
        return new Span(trace,UUID.randomUUID().toString().replace("-","").substring(0,16),parent);
    }
    public static Span current(UUID payment) { var span=CURRENT.get(); return span==null || !span.traceId().equals(payment.toString().replace("-","")) ? child(payment,null) : span; }
    public static void use(Span span) { CURRENT.set(span); }
    public static void clear() { CURRENT.remove(); }
    public static void record(JdbcTemplate jdbc, Span span, String event, String outcome, long started) {
        try { String json=jdbc.queryForObject("SELECT record_operation_telemetry('worker',?,?,?,?,?,?)::text",String.class,event,span.traceId(),span.spanId(),span.parentSpanId(),outcome,Math.max(0,(System.nanoTime()-started)/1000000));
            LoggerFactory.getLogger("pdv.telemetry").info("{}",json);
        } catch(org.springframework.dao.DataAccessException failure) { LoggerFactory.getLogger("pdv.telemetry").warn("{\"service\":\"worker\",\"event\":\"telemetry_unavailable\"}"); }
    }
    public record Span(String traceId,String spanId,String parentSpanId) {
        public String header() { return "00-"+traceId+"-"+spanId+"-01"; }
    }
}
