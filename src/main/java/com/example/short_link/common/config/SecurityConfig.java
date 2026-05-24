package com.example.short_link.common.config;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.web.util.matcher.RegexRequestMatcher.regexMatcher;

import com.example.short_link.common.web.RateLimitFilter;
import com.example.short_link.user.presentation.ApiKeyAuthenticationFilter;
import com.example.short_link.user.presentation.JsonAuthenticationEntryPoint;
import com.example.short_link.user.presentation.JwtAuthenticationFilter;
import com.example.short_link.user.presentation.OAuth2LoginFailureHandler;
import com.example.short_link.user.presentation.OAuth2LoginSuccessHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * Short-code surface — {@code /{shortCode}} for GET redirects and POST password unlocks. The
   * regex matches {@code RedirectController}'s path constraint exactly so the security layer can't
   * accidentally open paths the controller would never serve. Single-segment alphanumeric, 3–16
   * chars.
   */
  private static final String SHORT_CODE_REGEX = "^/[0-9A-Za-z]{3,16}$";

  /** OG preview image rendered for crawlers — same short-code shape with {@code /og.png} suffix. */
  private static final String OG_CARD_REGEX = "^/[0-9A-Za-z]{3,16}/og\\.png$";

  private final JwtAuthenticationFilter jwtFilter;
  private final ApiKeyAuthenticationFilter apiKeyFilter;
  private final OAuth2LoginSuccessHandler oauth2SuccessHandler;
  private final OAuth2LoginFailureHandler oauth2FailureHandler;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${short-link.cors.allowed-origins:http://localhost:3001}") String allowedOrigins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOriginPatterns(
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-Request-Id", "X-Pow-Challenge", "X-Pow-Nonce"));
    cors.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition"));
    cors.setAllowCredentials(true);
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  @Bean
  public RateLimitFilter rateLimitFilter(
      StringRedisTemplate redis,
      JsonMapper jsonMapper,
      RateLimitProperties rateLimit,
      MeterRegistry meterRegistry) {
    return new RateLimitFilter(
        redis, jsonMapper, rateLimit.anonymous(), rateLimit.authenticated(), meterRegistry);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ObjectProvider<ClientRegistrationRepository> clientRegistrations,
      RateLimitFilter rateLimitFilter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Infra: health probe, PoW challenge, OAuth callbacks.
                    .requestMatchers(GET, "/actuator/health", "/api/v1/pow/challenge")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    // Swagger docs are admin-only — the API surface is internal product, not public
                    // reference. Don't widen to permitAll without a docs gating story.
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .hasRole("ADMIN")

                    // Short-code surface. The regex matchers keep this from accidentally opening
                    // {@code GET /anything} the way {@code GET /*} did; the controller's path
                    // constraint and the security matcher are now declared in one place
                    // (SHORT_CODE_REGEX).
                    .requestMatchers(GET, "/")
                    .permitAll()
                    .requestMatchers(regexMatcher(GET, SHORT_CODE_REGEX))
                    .permitAll()
                    .requestMatchers(regexMatcher(POST, SHORT_CODE_REGEX))
                    .permitAll()
                    .requestMatchers(regexMatcher(GET, OG_CARD_REGEX))
                    .permitAll()

                    // Public read + low-trust write surface (rate-limited / PoW-gated upstream).
                    .requestMatchers(GET, "/api/v1/public/**")
                    .permitAll()
                    .requestMatchers(
                        POST, "/api/v1/public/email-leads", "/api/v1/public/profiles/*/visit")
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/links/*/public-stats", "/api/v1/links/*/stream")
                    .permitAll()
                    // Anonymous link creation — PoW filter sits in front of this in the controller
                    // path so it isn't an unauthenticated free-for-all.
                    .requestMatchers(POST, "/api/v1/links")
                    .permitAll()

                    // Auth flows that must work pre-login. /2fa/verify is the second factor after
                    // primary login (challenge token only, not full session).
                    .requestMatchers(
                        POST,
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/2fa/verify",
                        "/api/v1/auth/dev-login")
                    .permitAll()
                    // Stripe verifies its own signature inside the handler — auth-by-signature, not
                    // auth-by-session.
                    .requestMatchers(POST, "/api/v1/billing/webhook")
                    .permitAll()

                    // Admin surface.
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                    // Everything else (links management, profiles, billing checkout/portal,
                    // webhooks, custom-domains, tags, campaigns, 2FA setup, /users/me, ...) needs a
                    // session. Letting `anyRequest().authenticated()` cover them keeps the matcher
                    // list short and removes ~70 lines of per-endpoint matchers that all said the
                    // same thing.
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(apiKeyFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class);
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login(
          o -> o.successHandler(oauth2SuccessHandler).failureHandler(oauth2FailureHandler));
    }
    return http.build();
  }
}
