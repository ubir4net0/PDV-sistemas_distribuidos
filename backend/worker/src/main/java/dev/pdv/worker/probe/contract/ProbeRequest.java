package dev.pdv.worker.probe.contract;

import java.util.UUID;

/** Technical diagnostic only; not a payment or business event. */
public record ProbeRequest(String type, UUID requestId, String text) {
    public static final String TYPE = "pdv.probe.request.v1";

    public ProbeRequest {
        if (!TYPE.equals(type) || requestId == null || text == null || text.length() > 256) {
            throw new IllegalArgumentException("Invalid diagnostic contract");
        }
    }
}
