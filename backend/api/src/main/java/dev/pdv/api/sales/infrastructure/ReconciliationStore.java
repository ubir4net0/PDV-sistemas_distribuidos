package dev.pdv.api.sales.infrastructure;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.sales.application.ReconciliationModels.Execution;
import dev.pdv.api.sales.application.ReconciliationModels.Outcome;
import dev.pdv.api.sales.application.ReconciliationModels.Reconciliation;
import dev.pdv.api.sales.application.ReconciliationModels.Resolution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ReconciliationStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public ReconciliationStore(JdbcTemplate jdbc, JsonMapper json) { this.jdbc=jdbc; this.json=json; }
    public <T> Optional<Execution<T>> replay(UUID actor, UUID request, String fingerprint, Class<T> type) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(r,i)->0,"payment-command:"+actor+":"+request);
        return jdbc.query("SELECT fingerprint,response::text,http_status FROM payment_command_operations WHERE actor_id=? AND request_id=?",(r,i)->{
            if(!fingerprint.equals(r.getString(1))) { throw new ApiFailure(409,"request_conflict"); }
            return new Execution<>(json.readValue(r.getString(2),type),r.getInt(3),true);
        },actor,request).stream().findFirst();
    }
    public <T> Execution<T> record(UUID actor, UUID request, UUID payment, String kind, String fingerprint, T response, int status) {
        jdbc.update("INSERT INTO payment_command_operations(actor_id,request_id,payment_id,kind,fingerprint,response,http_status) VALUES (?,?,?,?,?,?::jsonb,?)",
                actor,request,payment,kind,fingerprint,json.writeValueAsString(response),status);
        return new Execution<>(response,status,false);
    }
    public Resolution resolve(UUID payment, UUID actor, Outcome outcome, String reason) {
        var sales=jdbc.query("SELECT sale_id FROM payments WHERE id=?",(r,i)->r.getObject(1,UUID.class),payment);
        if(sales.isEmpty()) { throw new ApiFailure(404,"payment_not_found"); }
        jdbc.query("SELECT id FROM sales WHERE id=? FOR UPDATE",(r,i)->r.getObject(1,UUID.class),sales.getFirst());
        var state=jdbc.queryForObject("SELECT status||':'||scenario FROM payments WHERE id=? FOR UPDATE",String.class,payment);
        var known=jdbc.query("SELECT outcome,reason,recorded_at FROM provider_resolutions WHERE payment_id=?",(r,i)->{
            if(!outcome.name().equals(r.getString(1)) || !reason.equals(r.getString(2))) { throw new ApiFailure(409,"resolution_conflict"); }
            return new Resolution(payment,outcome,r.getTimestamp(3).toInstant());
        },payment);
        if(!known.isEmpty()) { return known.getFirst(); }
        if(!"INDETERMINADO:TIMEOUT".equals(state)) { throw new ApiFailure(409,"resolution_unavailable"); }
        return jdbc.queryForObject("INSERT INTO provider_resolutions(payment_id,outcome,reason,actor_id) VALUES (?,?,?,?) RETURNING recorded_at",
                (r,i)->new Resolution(payment,outcome,r.getTimestamp(1).toInstant()),payment,outcome.name(),reason,actor);
    }
    public UUID ownPayment(UUID sale, UUID actor) {
        return jdbc.query("SELECT p.id FROM sales s JOIN payments p ON p.sale_id=s.id WHERE s.id=? AND s.actor_id=?",(r,i)->r.getObject(1,UUID.class),sale,actor)
            .stream().findFirst().orElseThrow(()->new ApiFailure(404,"sale_not_found"));
    }
    public Reconciliation reconcile(UUID sale, UUID payment) {
        jdbc.query("SELECT id FROM sales WHERE id=? FOR UPDATE",(r,i)->r.getObject(1,UUID.class),sale);
        var decision=jdbc.query("SELECT outcome FROM provider_resolutions WHERE payment_id=?",(r,i)->r.getString(1),payment);
        if(!decision.isEmpty()) { available(jdbc.queryForObject("SELECT apply_business_reconciliation(?,?)",String.class,payment,decision.getFirst())); }
        String state=jdbc.queryForObject("SELECT status FROM payments WHERE id=?",String.class,payment);
        if("APROVADO".equals(state)) { available(jdbc.queryForObject("SELECT confirm_business_sale(?)",String.class,payment)); }
        if("RECUSADO".equals(state)) { available(jdbc.queryForObject("SELECT release_business_sale(?)",String.class,payment)); }
        return jdbc.queryForObject("""
                SELECT s.status,p.status AS payment_status,r.status AS reservation_status,g.receipt_id
                FROM sales s JOIN payments p ON p.sale_id=s.id JOIN inventory_reservations r ON r.id=s.reservation_id JOIN sale_sagas g ON g.sale_id=s.id WHERE s.id=?
                """,(r,i)->new Reconciliation(sale,r.getString("payment_status"),r.getString("status"),r.getString("reservation_status"),
                    !java.util.List.of("APROVADO","RECUSADO").contains(r.getString("payment_status")),r.getObject("receipt_id",UUID.class)),sale);
    }
    private void available(String result) { if(result==null || java.util.List.of("BUSY","NOT_FOUND","NOT_STARTED").contains(result)) { throw new ApiFailure(409,"reconciliation_busy"); } }
}
