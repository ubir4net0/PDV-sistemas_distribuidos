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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Header-only authentication; no ambient browser credentials.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
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
                        .requestMatchers(HttpMethod.GET, "/access/permissions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/customers").authenticated()
                        .requestMatchers(HttpMethod.GET, "/customers", "/customers/{id}", "/customers/{id}/history").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/customers/{id}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/customers/{id}/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/customers/regions", "/customers/{id}/addresses").authenticated()
                        .requestMatchers(HttpMethod.POST, "/customers/{id}/addresses").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/customers/{id}/addresses/{addressId}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/categories", "/products").authenticated()
                        .requestMatchers(HttpMethod.POST, "/categories", "/products").authenticated()
                        .requestMatchers(HttpMethod.GET, "/categories/{id}", "/products/{id}", "/categories/{id}/history", "/products/{id}/history").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/categories/{id}", "/products/{id}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/categories/{id}/status", "/products/{id}/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/suppliers", "/suppliers/{id}", "/suppliers/{id}/history", "/suppliers/{id}/products").authenticated()
                        .requestMatchers(HttpMethod.POST, "/suppliers").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/suppliers/{id}", "/products/{id}/supplier").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/suppliers/{id}/status").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/suppliers/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/inventory", "/inventory/{productId}", "/inventory/{productId}/opening").authenticated()
                        .requestMatchers(HttpMethod.POST, "/inventory/{productId}/movements", "/inventory/movements/batches").authenticated()
                        .requestMatchers(HttpMethod.GET, "/inventory/{productId}/movements").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/inventory/{productId}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/reservations", "/reservations/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/reservations", "/reservations/{id}/renew").authenticated()
                        .requestMatchers(HttpMethod.GET, "/carts", "/carts/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/carts").authenticated()
                        .requestMatchers(HttpMethod.POST, "/carts/{id}/checkout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/sales/{id}", "/sales/{id}/receipt").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/carts/{id}/items/{productId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/carts/{id}/items/{productId}").authenticated()
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
