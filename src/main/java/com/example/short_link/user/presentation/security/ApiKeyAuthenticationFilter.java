package com.example.short_link.user.presentation.security;

import com.example.short_link.user.application.ApiKeyService;
import com.example.short_link.user.application.read.UserQueryService;
import com.example.short_link.user.domain.ApiKeyEntity;
import com.example.short_link.user.domain.UserEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves API keys passed as {@code Authorization: Bearer kurl_...} or {@code X-API-Key:
 * kurl_...}. Runs after the JWT filter so JWT tokens still take precedence; only kicks in when no
 * authentication has been set yet.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";
  private static final String HEADER_X = "X-API-Key";

  private final ApiKeyService apiKeyService;
  private final UserQueryService userQueryService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      String raw = extract(req);
      if (raw != null) {
        Optional<ApiKeyEntity> keyOpt = apiKeyService.resolve(raw);
        keyOpt.ifPresent(
            key -> {
              UserEntity user = userQueryService.findActive(key.getUserId()).orElse(null);
              if (user == null) return;
              var authorities =
                  List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
              var auth = new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
              SecurityContextHolder.getContext().setAuthentication(auth);
              MDC.put("userId", String.valueOf(user.getId()));
              MDC.put("authMethod", "api-key");
              apiKeyService.recordUsage(key.getId());
            });
      }
    }
    chain.doFilter(req, res);
  }

  private String extract(HttpServletRequest req) {
    String authHeader = req.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith(BEARER)) {
      String token = authHeader.substring(BEARER.length());
      if (token.startsWith(ApiKeyService.KEY_PREFIX)) return token;
    }
    String xKey = req.getHeader(HEADER_X);
    if (xKey != null && xKey.startsWith(ApiKeyService.KEY_PREFIX)) return xKey;
    return null;
  }
}
