package com.turfbooking.turf_service.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Matchers are evaluated in order, so "/turfs/mine" must precede the public GETs.
                        .requestMatchers(HttpMethod.GET, "/turfs/mine").hasRole("OWNER")
                        // Admin is deliberately absent here: the task only gave admins turf deletion.
                        .requestMatchers(HttpMethod.GET, "/turfs/{id}/timeslots").hasAnyRole("CUSTOMER", "OWNER")
                        // Browsing turfs stays public, as before.
                        .requestMatchers(HttpMethod.GET, "/turfs", "/turfs/{id}", "/sports").permitAll()
                        .requestMatchers(HttpMethod.POST, "/turfs", "/turfs/courts/*/timeslots").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/turfs/{id}").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/turfs/{id}/timeslots/{slotId}/status").hasRole("OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/turfs/{id}").hasAnyRole("OWNER", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    // auth-service puts the authority (e.g. ROLE_OWNER) in the "role" claim.
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
