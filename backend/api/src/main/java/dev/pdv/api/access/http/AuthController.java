package dev.pdv.api.access.http;

import java.util.List;
import java.util.UUID;
import dev.pdv.api.access.application.AuthPrincipal;
import dev.pdv.api.access.application.AuthService;
import dev.pdv.api.access.application.SessionHandle;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public AuthService.IssuedToken login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return new ProfileResponse(principal.id(), principal.displayName(), principal.email(),
                principal.roles(), principal.permissions());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal, Authentication auth) {
        service.logout(principal.id(), (SessionHandle) auth.getDetails());
        return ResponseEntity.noContent().build();
    }

    public record ProfileResponse(UUID id, String name, String email, List<String> roles, List<String> permissions) { }
}
