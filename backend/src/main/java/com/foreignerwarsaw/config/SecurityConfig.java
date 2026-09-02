package com.foreignerwarsaw.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Phase 2 authentication security config (ADR-005). Public endpoints are the account-lifecycle
 * endpoints that must be reachable before a session exists, plus the diagnostics endpoints already
 * public since Phase 1; everything else requires an authenticated session (brief §17/§18) -
 * server-side authorization is authoritative regardless of what Angular's route guards show.
 *
 * <p>401 vs 403 vs 404 (brief §19): an unauthenticated request to <em>any</em> non-public path gets
 * 401 via {@link RestAuthenticationEntryPoint}, whether or not that path actually exists - this is
 * standard REST API practice (route existence is itself only discoverable once authenticated) and
 * is what replaces Phase 1's blanket {@code denyAll()} 403. An authenticated request to a path with
 * no controller mapping reaches {@link com.foreignerwarsaw.common.web.GlobalExceptionHandler}'s
 * {@code NoResourceFoundException} handler and gets a real 404. 403 is reserved for an
 * authenticated principal that lacks a required authority ({@link RestAccessDeniedHandler}) - not
 * exercised by any Phase 2 endpoint yet (no role-gated endpoint exists until Phase 9's admin
 * panel), but wired now so adding one later needs no security-config change.
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
    // Read-only reference data (brief §32): populating a registration/onboarding
    // country or district dropdown must never require a session. No write endpoint
    // exists under this prefix at all in Phase 3 (brief §66) - admin editing is
    // Phase 9's job - so a blanket GET-only allow here doesn't risk exposing a
    // mutating route by accident.
    "/api/v1/reference/**",
    // Public, read-only procedure content (Phase 4, brief §36-38): only ever resolves
    // to currently active PUBLISHED versions (ProcedureQueryService) - no write
    // endpoint exists under this prefix, same reasoning as /reference/** above.
    "/api/v1/procedures/**",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs/**"
  };

  private static final String[] PUBLIC_POST_ENDPOINTS = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/verify-email",
    "/api/v1/auth/resend-verification",
    "/api/v1/auth/forgot-password",
    "/api/v1/auth/reset-password",
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS)
                    .permitAll()
                    // Phase 4 internal content-management API (brief §44) - most
                    // specific action first, since Spring Security's
                    // authorizeHttpRequests evaluates matchers in registration order
                    // and stops at the first match. Each matcher reflects brief §44's
                    // own CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN responsibility split -
                    // never a single blanket role check on the whole prefix.
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/internal/content/procedures/*/versions/*/publish")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/internal/content/procedures/*/versions/*/archive")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/internal/content/procedures/*/versions/*/approve")
                    .hasAnyRole("LEGAL_REVIEWER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/internal/content/sources/*/verify")
                    .hasAnyRole("LEGAL_REVIEWER", "ADMIN")
                    // Everything else under the prefix (create procedure/version, add
                    // step/document, create/attach source, submit) is CONTENT_EDITOR's
                    // own responsibility (brief §44) - ADMIN has "broader control" per
                    // the same section, so is included too; LEGAL_REVIEWER is
                    // deliberately NOT included here (their role is review/approve
                    // only, per brief §44's own responsibility split).
                    .requestMatchers("/api/v1/internal/content/**")
                    .hasAnyRole("CONTENT_EDITOR", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204))
                    .deleteCookies("SESSION")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true));
    return http.build();
  }

  /**
   * {@code createDelegatingPasswordEncoder()} is Spring Security's own current recommendation
   * (brief §6) - it stores the algorithm as a {@code {bcrypt}}-style prefix on the hash, defaults
   * new hashes to bcrypt, and lets a future algorithm migration happen by upgrading the encoder,
   * not by rewriting stored hashes.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  /**
   * Exists only so {@link DaoAuthenticationProvider}'s bean-presence auto-detection is unambiguous;
   * {@link com.foreignerwarsaw.user.AppUserDetailsService} plus {@link #passwordEncoder()} are
   * otherwise enough for Spring Boot to wire the provider automatically.
   */
  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * See {@link com.foreignerwarsaw.auth.LoginService} for why this needs to be invoked explicitly
   * during a manual (non-filter) login.
   */
  @Bean
  SessionAuthenticationStrategy sessionAuthenticationStrategy() {
    return new ChangeSessionIdAuthenticationStrategy();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    // Required for both the session cookie and the XSRF-TOKEN cookie to actually
    // travel on cross-origin requests in local development (brief §10/§45) - see
    // ADR-005 for why SameSite=Lax + this combination is still safe (localhost:4200
    // and localhost:8080 are same-site).
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
