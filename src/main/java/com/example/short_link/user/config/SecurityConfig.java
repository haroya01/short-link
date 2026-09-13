package com.example.short_link.user.config;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.web.util.matcher.RegexRequestMatcher.regexMatcher;

import com.example.short_link.common.config.RateLimitProperties;
import com.example.short_link.common.web.RateLimitCounter;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * Keep the path constraint aligned with RedirectController. RegexRequestMatcher includes the
   * query string, so the optional query suffix is needed for tracked public links to bypass session
   * authentication.
   */
  static final String SHORT_CODE_REGEX = "^/[0-9A-Za-z]{3,16}(\\?.*)?$";

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
      RateLimitCounter counter,
      JsonMapper jsonMapper,
      RateLimitProperties rateLimit,
      MeterRegistry meterRegistry) {
    return new RateLimitFilter(
        counter, jsonMapper, rateLimit.anonymous(), rateLimit.authenticated(), meterRegistry);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ObjectProvider<ClientRegistrationRepository> clientRegistrations,
      RateLimitFilter rateLimitFilter)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(c -> {})
        // Spring supplies nosniff and frame denial; restrict referrer details across origins too.
        .headers(
            h ->
                h.httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    .referrerPolicy(
                        rp ->
                            rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        GET,
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/api/v1/pow/challenge")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    // Keep the internal API documentation restricted to administrators.
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                    .requestMatchers(GET, "/")
                    .permitAll()
                    .requestMatchers(regexMatcher(GET, SHORT_CODE_REGEX))
                    .permitAll()
                    .requestMatchers(regexMatcher(POST, SHORT_CODE_REGEX))
                    .permitAll()
                    .requestMatchers(regexMatcher(GET, OG_CARD_REGEX))
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/public/**")
                    .permitAll()
                    .requestMatchers(
                        POST,
                        "/api/v1/public/email-leads",
                        "/api/v1/public/profiles/*/visit",
                        "/api/v1/public/profiles/*/posts/*/view",
                        "/api/v1/public/behavior-events",
                        "/api/v1/public/abuse-reports",
                        // 이벤트 신청/취소 — 참가자는 계정이 없다. 신청은 PoW 게이트가 컨트롤러에서 막고,
                        // 취소는 소지자만 아는 토큰이 자격증명.
                        "/api/v1/public/events/*/registrations",
                        "/api/v1/public/events/registrations/cancel")
                    .permitAll()
                    // EventSource는 Authorization 헤더를 못 보내므로 컨트롤러가 단명 streamToken을 검증한다.
                    .requestMatchers(
                        GET,
                        "/api/v1/links/*/public-stats",
                        "/api/v1/links/*/stream",
                        "/api/v1/users/me/clicks/stream")
                    .permitAll()
                    // 컨트롤러가 isVisibleTo()로 비공개 컬렉션을 404로 감춘다. 단일 세그먼트만 허용해
                    // 하위 connections 등의 쓰기 경로는 인증을 유지한다.
                    .requestMatchers(GET, "/api/v1/collections/*")
                    .permitAll()
                    // Controllers support a null viewer for public reads; mutations require a
                    // session.
                    .requestMatchers(GET, "/api/v1/users/*/follow")
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/users/*/followers", "/api/v1/users/*/following")
                    .permitAll()
                    // Anonymous creation is protected by PoW in the controller.
                    .requestMatchers(POST, "/api/v1/links")
                    .permitAll()

                    // 2FA verification authenticates with the primary-login challenge, before a
                    // session exists.
                    .requestMatchers(
                        POST,
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/2fa/verify",
                        // The Apple identity token is the credential; the verifier checks Apple's
                        // signature and claims.
                        "/api/v1/auth/apple",
                        "/api/v1/auth/dev-login")
                    .permitAll()
                    // Mobile logout authorizes with the supplied refresh token; no access session
                    // is required.
                    .requestMatchers(GET, "/api/v1/auth/mobile/start")
                    .permitAll()
                    .requestMatchers(
                        POST,
                        "/api/v1/auth/mobile/exchange",
                        "/api/v1/auth/mobile/refresh",
                        "/api/v1/auth/mobile/2fa/verify",
                        "/api/v1/auth/mobile/apple",
                        "/api/v1/auth/mobile/logout")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
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
