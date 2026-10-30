package dev.pdv.worker.probe.contract;

import java.util.UUID;

public record ProbeResponse(String type, UUID requestId, String text) {
    public static ProbeResponse from(ProbeRequest request) {
        return new ProbeResponse("pdv.probe.response.v1", request.requestId(), request.text());
    }
}
