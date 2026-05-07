package com.example.short_link.user.api;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.ParsedAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";

  private final JwtTokenService jwt;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER)) {
      String token = header.substring(BEARER.length());
      try {
        ParsedAccess parsed = jwt.parseAccessTokenDetailed(token);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role()));
        var auth = new UsernamePasswordAuthenticationToken(parsed.userId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        MDC.put("userId", String.valueOf(parsed.userId()));
      } catch (Exception ignored) {
      }
    }
    chain.doFilter(req, res);
  }
}
