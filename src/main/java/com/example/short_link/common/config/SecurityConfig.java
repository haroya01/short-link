package com.example.short_link.common.config;

import com.example.short_link.common.api.RateLimitFilter;
import com.example.short_link.user.api.ApiKeyAuthenticationFilter;
import com.example.short_link.user.api.JsonAuthenticationEntryPoint;
import com.example.short_link.user.api.JwtAuthenticationFilter;
import com.example.short_link.user.api.OAuth2LoginFailureHandler;
import com.example.short_link.user.api.OAuth2LoginSuccessHandler;
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
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
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
      @Value("${short-link.rate-limit.anonymous:100}") long anonymousLimit,
      @Value("${short-link.rate-limit.authenticated:1000}") long authenticatedLimit,
      MeterRegistry meterRegistry) {
    return new RateLimitFilter(
        redis, jsonMapper, anonymousLimit, authenticatedLimit, meterRegistry);
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
                auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/public/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/links/*/public-stats")
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
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/v1/users/me/**")
                    .authenticated()
                    .requestMatchers("/api/v1/users/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/dev-login")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .permitAll())
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
