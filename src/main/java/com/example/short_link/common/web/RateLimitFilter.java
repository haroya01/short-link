package com.example.short_link.common.web;

import io.micrometer.core.instrument.MeterRegistry;
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
import tools.jackson.databind.json.JsonMapper;

public class RateLimitFilter extends OncePerRequestFilter {

  static final String METRIC_NAME = "rate_limit.exceeded";

  private static final Duration WINDOW = Duration.ofMinutes(1);

  // Sensitive auth endpoints get a tighter per-IP bucket on top of the global limit. Brute-force
  // against a 6-digit 2FA OTP would otherwise sit comfortably under the global anonymous cap
  // (100/min) — that's too generous when the endpoint's only defense is the limit itself.
  private record EndpointRule(String method, String path, long perMinute) {}

  // dev-login 은 의도적 제외 — DevAuthController 자체가 dev profile 한정이라 prod 노출 없음.
  // 테스트 fixture 가 dev-login 을 호출해 user 를 만드는 경로가 광범위해 endpoint rate 룰이
  // 오히려 false-positive 만 만든다.
  private static final List<EndpointRule> ENDPOINT_RULES =
      List.of(
          new EndpointRule("POST", "/api/v1/auth/2fa/verify", 5),
          new EndpointRule("POST", "/api/v1/2fa/confirm", 5),
          new EndpointRule("POST", "/api/v1/2fa/disable", 5),
          new EndpointRule("POST", "/api/v1/auth/refresh", 10));

  private static final RedisScript<Long> INCR_AND_EXPIRE =
      new DefaultRedisScript<>(
          "local c = redis.call('INCR', KEYS[1]) "
              + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
              + "return c",
          Long.class);

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;
  private final long anonymousLimit;
  private final long authenticatedLimit;
  private final MeterRegistry meterRegistry;

  public RateLimitFilter(
      StringRedisTemplate redis,
      JsonMapper jsonMapper,
      long anonymousLimit,
      long authenticatedLimit,
      MeterRegistry meterRegistry) {
    this.redis = redis;
    this.jsonMapper = jsonMapper;
    this.anonymousLimit = anonymousLimit;
    this.authenticatedLimit = authenticatedLimit;
    this.meterRegistry = meterRegistry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String uri = req.getRequestURI();
    if (uri.startsWith("/actuator/")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")) {
      chain.doFilter(req, res);
      return;
    }

    String method = req.getMethod();
    String clientIp = ClientIp.of(req);

    for (EndpointRule rule : ENDPOINT_RULES) {
      if (rule.method().equals(method) && rule.path().equals(uri)) {
        String epKey = "rate:ep:" + method + ":" + uri + ":ip:" + clientIp;
        Long epCount =
            redis.execute(INCR_AND_EXPIRE, List.of(epKey), String.valueOf(WINDOW.getSeconds()));
        if (epCount != null && epCount > rule.perMinute()) {
          meterRegistry.counter(METRIC_NAME, "scope", "endpoint", "path", uri).increment();
          writeRateLimitResponse(req, res);
          return;
        }
        break;
      }
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String identifier;
    long limit;
    if (auth != null && auth.getPrincipal() instanceof Long userId) {
      identifier = "user:" + userId;
      limit = authenticatedLimit;
    } else {
      identifier = "ip:" + clientIp;
      limit = anonymousLimit;
    }
    String key = "rate:" + identifier;
    Long count = redis.execute(INCR_AND_EXPIRE, List.of(key), String.valueOf(WINDOW.getSeconds()));
    if (count != null && count > limit) {
      meterRegistry
          .counter(
              METRIC_NAME,
              "scope",
              auth != null && auth.getPrincipal() instanceof Long ? "user" : "anonymous")
          .increment();
      writeRateLimitResponse(req, res);
      return;
    }
    chain.doFilter(req, res);
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
    jsonMapper.writeValue(res.getOutputStream(), body);
  }
}
