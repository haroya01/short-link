package com.example.short_link.common.config;

import com.example.short_link.common.api.RateLimitFilter;
import com.example.short_link.user.api.JsonAuthenticationEntryPoint;
import com.example.short_link.user.api.JwtAuthenticationFilter;
import com.example.short_link.user.api.OAuth2LoginSuccessHandler;
import io.micrometer.core.instrument.MeterRegistry;
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
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final OAuth2LoginSuccessHandler oauth2SuccessHandler;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;

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
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
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
                    .requestMatchers(
                        "/api/v1/links/me",
                        "/api/v1/auth/logout",
                        "/api/v1/users/me",
                        "/api/v1/users/me/preferences")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/dev-login")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login(o -> o.successHandler(oauth2SuccessHandler));
    }
    return http.build();
  }
}
