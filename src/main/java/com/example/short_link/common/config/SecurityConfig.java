package com.example.short_link.common.config;

import com.example.short_link.user.api.JwtAuthenticationFilter;
import com.example.short_link.user.api.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final OAuth2LoginSuccessHandler oauth2SuccessHandler;

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, ObjectProvider<ClientRegistrationRepository> clientRegistrations)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.PATCH, "/api/v1/links/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/links/*")
                    .authenticated()
                    .requestMatchers("/api/v1/links/me", "/api/v1/auth/logout")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login(o -> o.successHandler(oauth2SuccessHandler));
    }
    return http.build();
  }
}
