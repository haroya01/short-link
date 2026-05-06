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
import java.util.List;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private static final RedisScript<Long> INCR_AND_EXPIRE =
      new DefaultRedisScript<>(
          "local c = redis.call('INCR', KEYS[1]) "
              + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
              + "return c",
          Long.class);

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
    if (req.getRequestURI().startsWith("/actuator/")) {
      chain.doFilter(req, res);
      return;
    }

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
    Long count = redis.execute(INCR_AND_EXPIRE, List.of(key), String.valueOf(WINDOW.getSeconds()));
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
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      body.setProperty("requestId", requestId);
    }

    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setHeader("Retry-After", "60");
    objectMapper.writeValue(res.getOutputStream(), body);
  }
}
