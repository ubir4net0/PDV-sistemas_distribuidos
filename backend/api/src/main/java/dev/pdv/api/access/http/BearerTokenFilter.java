package dev.pdv.api.access.http;

import java.io.IOException;
import java.util.Collections;
import dev.pdv.api.access.application.AuthService;
import dev.pdv.api.access.application.SessionHandle;
import dev.pdv.api.access.application.TokenCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final AuthService service;

    public BearerTokenFilter(AuthService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return ("POST".equals(request.getMethod()) && "/auth/login".equals(path))
                || ("GET".equals(request.getMethod()) && ("/actuator/health".equals(path)
                || "/actuator/health/liveness".equals(path) || "/actuator/health/readiness".equals(path)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (headers.size() != 1 || !headers.getFirst().matches("(?i:Bearer) [A-Za-z0-9_-]{43}")) {
            SafeErrors.write(response, 401, "unauthorized");
            return;
        }
        try {
            String digest = TokenCodec.digest(headers.getFirst().substring(7));
            var principal = service.resolve(digest);
            var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
            principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
            authentication.setDetails(new SessionHandle(digest));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (AuthenticationException exception) {
            SafeErrors.write(response, 401, "unauthorized");
            return;
        } catch (DataAccessException exception) {
            SafeErrors.write(response, 503, "service_unavailable");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
