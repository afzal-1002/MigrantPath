package com.foreignerwarsaw.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Phase 1 security baseline (brief §28): no authentication mechanism exists yet, so only the
 * minimal public platform/health/docs endpoints are reachable and everything else is explicitly
 * denied - not left "open by accident" behind Spring Security's default permit-all-if-unconfigured
 * behavior. Phase 2 (IMPLEMENTATION_PLAN.md §2) replaces {@code anyRequest().denyAll()} with real
 * per-endpoint authorization once accounts, roles, and sessions exist; it also revisits CSRF policy
 * once cookie-based sessions are introduced (ADR-005) - CSRF is not a meaningful concern yet
 * because there is no session/cookie authentication for a forged request to ride on.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

  private static final String[] PUBLIC_GET_ENDPOINTS = {
    "/actuator/health",
    "/actuator/health/**",
    "/actuator/info",
    "/api/v1/platform/status",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs/**"
  };

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
