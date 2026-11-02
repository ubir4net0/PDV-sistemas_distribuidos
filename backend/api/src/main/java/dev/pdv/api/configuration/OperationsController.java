package dev.pdv.api.configuration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsController {
    private final OperationTelemetry telemetry;
    public OperationsController(OperationTelemetry telemetry) { this.telemetry=telemetry; }
    @GetMapping("/operations/telemetry")
    public ResponseEntity<OperationTelemetry.Dashboard> telemetry(@RequestParam(required=false) String traceId) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(telemetry.dashboard(traceId));
    }
}
