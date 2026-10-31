package dev.pdv.api.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import dev.pdv.api.access.application.AuthService;
import dev.pdv.api.access.http.BearerTokenFilter;
import dev.pdv.api.access.http.SafeErrors;
import dev.pdv.api.access.infrastructure.IdentityRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Header-only authentication; no ambient browser credentials.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService identities(IdentityRepository repository) {
        return email -> {
            var identity = repository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(identity.id().toString()).password(identity.passwordHash())
                    .disabled(!identity.active()).authorities("AUTHENTICATED").build();
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService identities, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(identities);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain scaffoldSecurity(HttpSecurity http, AuthService auth) throws Exception {
        return http
                // Explicit Bearer headers only; session/cookie authentication is disabled.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new BearerTokenFilter(auth), AnonymousAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                SafeErrors.write(response, 401, "unauthorized"))
                        .accessDeniedHandler((request, response, exception) ->
                                SafeErrors.write(response, 403, "forbidden")))
                .build();
    }
}
