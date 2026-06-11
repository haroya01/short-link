package com.example.short_link.user.config;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.web.util.matcher.RegexRequestMatcher.regexMatcher;

import com.example.short_link.common.config.RateLimitProperties;
import com.example.short_link.common.web.RateLimitFilter;
import com.example.short_link.user.presentation.security.ApiKeyAuthenticationFilter;
import com.example.short_link.user.presentation.security.JsonAuthenticationEntryPoint;
import com.example.short_link.user.presentation.security.JwtAuthenticationFilter;
import com.example.short_link.user.presentation.security.OAuth2LoginFailureHandler;
import com.example.short_link.user.presentation.security.OAuth2LoginSuccessHandler;
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
   * Short-code surface — {@code /{shortCode}} for GET redirects and POST password unlocks.
   * Single-segment alphanumeric, 3–16 chars, matching {@code RedirectController}'s path constraint
   * so the security layer can't open paths the controller would never serve.
   *
   * <p>The trailing {@code (\?.*)?} is required: {@link
   * org.springframework.security.web.util.matcher.RegexRequestMatcher} matches against the URL
   * <em>including the query string</em>, so a tracked link like {@code /abc123?src=kakao} or a UTM
   * link ({@code ?utm_source=…}) wouldn't match a query-less anchor — it would fall through to
   * {@code anyRequest().authenticated()} and 401 instead of redirecting. {@code src} is a
   * first-class redirect feature ({@code RedirectController} reads it), so query strings must be
   * permitted here.
   */
  static final String SHORT_CODE_REGEX = "^/[0-9A-Za-z]{3,16}(\\?.*)?$";

  /** OG preview image rendered for crawlers — same short-code shape with {@code /og.png} suffix. */
  static final String OG_CARD_REGEX = "^/[0-9A-Za-z]{3,16}/og\\.png(\\?.*)?$";

  private final JwtAuthenticationFilter jwtFilter;
  private final ApiKeyAuthenticationFilter apiKeyFilter;
  private final OAuth2LoginSuccessHandler oauth2SuccessHandler;
  private final OAuth2LoginFailureHandler oauth2FailureHandler;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${short-link.cors.allowed-origins:http://localhost:3001}") String allowedOrigins,
      @Value("${spring.profiles.active:}") String activeProfile) {
    List<String> origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    boolean isProd =
        Arrays.stream(activeProfile.split(","))
            .map(String::trim)
            .anyMatch("prod"::equalsIgnoreCase);
    if (isProd) {
      if (origins.isEmpty()) {
        throw new IllegalStateException(
            "short-link.cors.allowed-origins must be set in prod profile");
      }
      for (String origin : origins) {
        if ("*".equals(origin) || origin.contains("localhost") || origin.contains("127.0.0.1")) {
          throw new IllegalStateException(
              "short-link.cors.allowed-origins must not contain wildcard or localhost in prod: "
                  + origin);
        }
      }
    }
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOriginPatterns(origins);
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
                    .requestMatchers(
                        GET,
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/api/v1/pow/challenge")
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
                        POST,
                        "/api/v1/public/email-leads",
                        "/api/v1/public/profiles/*/visit",
                        "/api/v1/public/profiles/*/posts/*/view",
                        "/api/v1/public/abuse-reports")
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/links/*/public-stats", "/api/v1/links/*/stream")
                    .permitAll()
                    // Author follower count is public; following-state needs auth but the
                    // controller reads a null principal for anonymous viewers. PUT/DELETE stay
                    // authenticated via anyRequest().
                    .requestMatchers(GET, "/api/v1/users/*/follow")
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
                    // Native-app auth: /start opens the OAuth dance from the browser sheet, the
                    // rest speak tokens in the body (Keychain, not cookies). /logout is permitAll
                    // because presenting the refresh token IS the authorization to kill it.
                    .requestMatchers(GET, "/api/v1/auth/mobile/start")
                    .permitAll()
                    .requestMatchers(
                        POST,
                        "/api/v1/auth/mobile/exchange",
                        "/api/v1/auth/mobile/refresh",
                        "/api/v1/auth/mobile/2fa/verify",
                        "/api/v1/auth/mobile/logout")
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
