package dev.pdv.api.sales.application;

import dev.pdv.api.sales.application.ReconciliationModels.Execution;
import dev.pdv.api.sales.application.ReconciliationModels.Reconciliation;
import dev.pdv.api.sales.application.ReconciliationModels.Resolution;
import dev.pdv.api.sales.application.ReconciliationModels.Resolve;
import dev.pdv.api.sales.infrastructure.ReconciliationStore;
import dev.pdv.api.configuration.ApiFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final ReconciliationStore store;
    public ReconciliationService(ReconciliationStore store) { this.store=store; }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('simulation.resolve')")
    public Execution<Resolution> resolve(UUID payment, Resolve input, UUID actor) {
        String reason=input.reason().strip(); String fingerprint=digest("RESOLUTION:"+payment+":"+input.outcome()+":"+reason);
        if(reason.isEmpty()) { throw new ApiFailure(400,"invalid_request"); }
        var replay=store.replay(actor,input.requestId(),fingerprint,Resolution.class); if(replay.isPresent()) { return replay.get(); }
        return store.record(actor,input.requestId(),payment,"RESOLUTION",fingerprint,store.resolve(payment,actor,input.outcome(),reason),200);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('sale.reconcile')")
    public Execution<Reconciliation> reconcile(UUID sale, UUID request, UUID actor) {
        UUID payment=store.ownPayment(sale,actor); String fingerprint=digest("RECONCILE:"+sale);
        var replay=store.replay(actor,request,fingerprint,Reconciliation.class); if(replay.isPresent()) { return replay.get(); }
        var result=store.reconcile(sale,payment); return store.record(actor,request,payment,"RECONCILE",fingerprint,result,result.pending()?202:200);
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
}
