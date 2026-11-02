package dev.pdv.api.operations;

import dev.pdv.api.configuration.ApiFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MessagingService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public MessagingService(JdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc; this.json=json; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('ADMIN')")
    public MessagingModels.Dashboard dashboard(String status,int page,int size) {
        if((status!=null && !List.of("PENDING","PUBLISHED","DEAD").contains(status)) || page<0 || size<1 || size>100) { throw new ApiFailure(400,"invalid_request"); }
        String filter=status==null ? "" : " WHERE d.status=?"; Object[] countArgs=status==null ? new Object[0] : new Object[]{status};
        Object[] args=status==null ? new Object[]{size,(long)page*size} : new Object[]{status,size,(long)page*size};
        var items=jdbc.query("SELECT e.id,e.event_type,d.status,d.attempts,d.version,d.last_error,d.updated_at,EXISTS(SELECT 1 FROM inbox_messages i WHERE i.event_id=e.id AND i.consumer=CASE WHEN e.event_type='pdv.payment.requested.v1' THEN 'payment-simulator-v1' ELSE 'sale-coordinator-v1' END) AS processed FROM outbox_events e JOIN outbox_deliveries d ON d.event_id=e.id"+filter+" ORDER BY d.updated_at DESC,e.id LIMIT ? OFFSET ?",
                (r,i)->new MessagingModels.Delivery(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getInt(4),r.getLong(5),r.getString(6),r.getTimestamp(7).toInstant(),r.getBoolean(8)),args);
        long total=jdbc.queryForObject("SELECT count(*) FROM outbox_deliveries d"+filter,Long.class,countArgs);
        var counts=jdbc.queryForObject("SELECT count(*) FILTER(WHERE status='PENDING'),count(*) FILTER(WHERE status='DEAD'),count(*) FILTER(WHERE status='PUBLISHED') FROM outbox_deliveries",(r,i)->new long[]{r.getLong(1),r.getLong(2),r.getLong(3)});
        Instant sync=jdbc.queryForObject("SELECT max(processed_at) FROM inbox_messages",(r,i)->{ var at=r.getTimestamp(1); return at==null ? null : at.toInstant(); });
        var queues=jdbc.query("SELECT *,observed_at<clock_timestamp()-interval '15 seconds' AS stale FROM messaging_queue_samples ORDER BY namespace,kind LIMIT 60",
                (r,i)->new MessagingModels.Queue(r.getString("namespace"),r.getString("kind"),r.getBoolean("available"),(Long)r.getObject("ready"),(Integer)r.getObject("consumers"),r.getTimestamp("observed_at").toInstant(),r.getBoolean("stale")));
        return new MessagingModels.Dashboard(Instant.now(),counts[0],counts[1],counts[2],sync,queues,new MessagingModels.Page(items,total,page,size));
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MessagingModels.Execution retry(UUID event,MessagingModels.Command input,UUID actor) {
        String reason=input.reason().trim(); if(reason.isEmpty() || reason.chars().anyMatch(c->c<32 || c==127)) { throw new ApiFailure(400,"invalid_request"); }
        String fingerprint=digest(event+":"+input.version()+":"+reason);
        jdbc.execute("SET LOCAL lock_timeout='2s'"); jdbc.execute("SET LOCAL statement_timeout='4s'");
        lock("messaging-retry:"+actor+":"+input.requestId());
        var known=jdbc.query("SELECT fingerprint,response::text FROM messaging_retry_operations WHERE actor_id=? AND request_id=?",(r,i)->{
            if(!fingerprint.equals(r.getString(1))) { throw new ApiFailure(409,"request_conflict"); }
            return new MessagingModels.Execution(json.readValue(r.getString(2),MessagingModels.Retry.class),true);
        },actor,input.requestId()); if(!known.isEmpty()) { return known.getFirst(); }
        var rows=jdbc.query("SELECT e.event_type,d.status,d.version FROM outbox_events e JOIN outbox_deliveries d ON d.event_id=e.id WHERE e.id=? FOR UPDATE OF d",
                (r,i)->new State(r.getString(1),r.getString(2),r.getLong(3)),event);
        if(rows.isEmpty()) { throw new ApiFailure(404,"event_not_found"); } var state=rows.getFirst();
        if(state.version()!=input.version() || state.version()==Long.MAX_VALUE || !List.of("DEAD","PUBLISHED").contains(state.status())) { throw new ApiFailure(409,"retry_unavailable"); }
        String consumer="pdv.payment.requested.v1".equals(state.type()) ? "payment-simulator-v1" : "sale-coordinator-v1";
        lock("inbox:"+consumer+":"+event);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inbox_messages WHERE consumer=? AND event_id=?)",Boolean.class,consumer,event))) { throw new ApiFailure(409,"retry_unavailable"); }
        jdbc.update("UPDATE outbox_deliveries SET status='PENDING',attempts=0,last_error=NULL,next_attempt_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE event_id=?",event);
        Instant at=jdbc.queryForObject("SELECT clock_timestamp()",(r,i)->r.getTimestamp(1).toInstant());
        var result=new MessagingModels.Retry(input.requestId(),event,"PENDING",state.version()+1,at);
        jdbc.update("INSERT INTO messaging_retry_operations(actor_id,request_id,event_id,fingerprint,reason,response) VALUES (?,?,?,?,?,?::jsonb)",actor,input.requestId(),event,fingerprint,reason,json.writeValueAsString(result));
        return new MessagingModels.Execution(result,false);
    }
    private void lock(String key) { jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(r,i)->0,key); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable",failure); }
    }
    private record State(String type,String status,long version) { }
}
