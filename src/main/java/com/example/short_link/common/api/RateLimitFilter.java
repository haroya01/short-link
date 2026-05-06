package com.example.short_link.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final long anonymousLimit;
  private final long authenticatedLimit;

  public RateLimitFilter(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      long anonymousLimit,
      long authenticatedLimit) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.anonymousLimit = anonymousLimit;
    this.authenticatedLimit = authenticatedLimit;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String identifier;
    long limit;
    if (auth != null && auth.getPrincipal() instanceof Long userId) {
      identifier = "user:" + userId;
      limit = authenticatedLimit;
    } else {
      identifier = "ip:" + clientIp(req);
      limit = anonymousLimit;
    }
    String key = "rate:" + identifier;
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, WINDOW);
    }
    if (count != null && count > limit) {
      writeRateLimitResponse(req, res);
      return;
    }
    chain.doFilter(req, res);
  }

  private String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }

  private void writeRateLimitResponse(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded");
    body.setInstance(URI.create(req.getRequestURI()));
    body.setProperty("code", "RATE_LIMITED");
    body.setProperty("timestamp", Instant.now().toString());

    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setHeader("Retry-After", "60");
    objectMapper.writeValue(res.getOutputStream(), body);
  }
}
