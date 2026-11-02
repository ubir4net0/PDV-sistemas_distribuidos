package dev.pdv.api.operations;

import dev.pdv.api.access.application.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessagingController {
    private final MessagingService service;
    public MessagingController(MessagingService service) { this.service=service; }
    @GetMapping("/operations/messaging")
    public ResponseEntity<MessagingModels.Dashboard> dashboard(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(service.dashboard(status,page,size));
    }
    @PostMapping("/operations/messaging/{eventId}/retry")
    public ResponseEntity<MessagingModels.Retry> retry(@PathVariable UUID eventId,@Valid @RequestBody MessagingModels.Command input,@AuthenticationPrincipal AuthPrincipal actor) {
        var result=service.retry(eventId,input,actor.id()); return ResponseEntity.ok().header("Cache-Control","no-store").header("Idempotent-Replay",Boolean.toString(result.replayed())).body(result.response());
    }
}
