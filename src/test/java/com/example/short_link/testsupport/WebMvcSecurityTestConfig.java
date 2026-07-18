package com.example.short_link.testsupport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@TestConfiguration
public class WebMvcSecurityTestConfig {

  public static final String USER_ID_HEADER = "X-Test-User-Id";
  public static final String ROLES_HEADER = "X-Test-Roles";

  @Bean
  OncePerRequestFilter headerAuthenticationFilter() {
    return new HeaderAuthenticationFilter();
  }

  @Bean
  WebMvcConfigurer authenticationPrincipalResolver() {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
      }
    };
  }

  private static final class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      String userId = request.getHeader(USER_ID_HEADER);
      if (userId != null && !userId.isBlank()) {
        String roles = request.getHeader(ROLES_HEADER);
        var authorities =
            Arrays.stream((roles == null || roles.isBlank() ? "USER" : roles).split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication =
            new TestingAuthenticationToken(Long.valueOf(userId), "test-credentials", authorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } else if (!isAnonymousAllowed(request)) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
      try {
        filterChain.doFilter(request, response);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }

    private static boolean isAnonymousAllowed(HttpServletRequest request) {
      String method = request.getMethod();
      String uri = request.getRequestURI();
      return ("POST".equals(method) && "/api/v1/billing/webhook".equals(uri))
          || ("POST".equals(method) && "/api/v1/auth/dev-login".equals(uri))
          // 행동 비콘은 프로드 SecurityConfig 도 익명 POST 를 연다 — 슬라이스도 같은 계약.
          || ("POST".equals(method) && "/api/v1/public/behavior-events".equals(uri))
          || ("GET".equals(method) && "/actuator/health".equals(uri))
          || ("GET".equals(method) && uri.startsWith("/api/v1/public/"));
    }
  }
}
