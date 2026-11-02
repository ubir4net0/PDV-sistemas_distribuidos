package dev.pdv.worker.business;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public record BusinessEnvelope(String type, UUID eventId, UUID paymentId, String outcome) {
    public static final String REQUEST = "pdv.payment.requested.v1";
    public static final String RESULT = "pdv.payment.result.v1";
    public static final String CONFIRM = "pdv.sale.confirm.v1";
    public static final String RECONCILED = "pdv.payment.reconciled.v1";
    public static final String RELEASE = "pdv.sale.release.v1";
    public BusinessEnvelope {
        if (eventId==null || paymentId==null || (!REQUEST.equals(type) && !coordinator(type))
                || (REQUEST.equals(type) && outcome!=null) || (CONFIRM.equals(type) && !"APROVADO".equals(outcome))
                || (RELEASE.equals(type) && !"RECUSADO".equals(outcome))
                || (RECONCILED.equals(type) && !List.of("APROVADO", "RECUSADO").contains(outcome==null ? "" : outcome))
                || (RESULT.equals(type) && !List.of("APROVADO", "RECUSADO", "INDETERMINADO").contains(outcome==null ? "" : outcome))) {
            throw new Rejected("Invalid business envelope");
        }
    }
    public static boolean coordinator(String type) { return List.of(RESULT, CONFIRM, RECONCILED, RELEASE).contains(type==null ? "" : type); }
    public String fingerprint() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((type+":"+eventId+":"+paymentId+":"+outcome).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    public static final class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }
}
