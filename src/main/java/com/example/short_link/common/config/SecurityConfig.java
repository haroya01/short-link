package com.example.short_link.common.config;

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
import org.springframework.http.HttpMethod;
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
                auth.requestMatchers(HttpMethod.GET, "/")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/pow/challenge")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/public/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/public/email-leads")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/public/profiles/*/visit")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/*/og.png")
                    .permitAll()
                    .requestMatchers("/api/v1/links/*/profile")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/public-stats")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/stream")
                    .permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/links/*/protection")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/users/me/claim-anonymous")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/links")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/links/bulk")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/links/*/visibility")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/links/*/og")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/links/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/links/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/stats")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/events")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/events.csv")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/stats.csv")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/detail")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/tags")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/links/*/tags")
                    .authenticated()
                    .requestMatchers("/api/v1/tags/**")
                    .authenticated()
                    .requestMatchers("/api/v1/tags")
                    .authenticated()
                    .requestMatchers("/api/v1/links/*/webhooks/**")
                    .authenticated()
                    .requestMatchers("/api/v1/links/*/webhooks")
                    .authenticated()
                    .requestMatchers("/api/v1/links/*/destinations/**")
                    .authenticated()
                    .requestMatchers("/api/v1/links/*/destinations")
                    .authenticated()
                    .requestMatchers("/api/v1/links/*/blocked-countries")
                    .authenticated()
                    .requestMatchers("/api/v1/custom-domains/**")
                    .authenticated()
                    .requestMatchers("/api/v1/custom-domains")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/2fa/verify")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    .requestMatchers("/api/v1/2fa/**")
                    .authenticated()
                    .requestMatchers("/api/v1/users/me/**")
                    .authenticated()
                    .requestMatchers("/api/v1/users/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/dev-login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhook")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/billing/checkout")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/billing/portal")
                    .authenticated()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/campaigns/**")
                    .authenticated()
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
