package com.example.short_link.common.web;

import com.example.short_link.common.web.response.ProblemDetails;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

public class RateLimitFilter extends OncePerRequestFilter {

  static final String METRIC_NAME = "rate_limit.exceeded";

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

  private final RateLimitCounter counter;
  private final JsonMapper jsonMapper;
  private final long anonymousLimit;
  private final long authenticatedLimit;
  private final MeterRegistry meterRegistry;

  public RateLimitFilter(
      RateLimitCounter counter,
      JsonMapper jsonMapper,
      long anonymousLimit,
      long authenticatedLimit,
      MeterRegistry meterRegistry) {
    this.counter = counter;
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
        Long epCount = counter.incrementEndpoint(method, uri, clientIp);
        if (epCount != null && epCount > rule.perMinute()) {
          meterRegistry.counter(METRIC_NAME, "scope", "endpoint", "path", uri).increment();
          writeRateLimitResponse(req, res);
          return;
        }
        break;
      }
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Long count;
    long limit;
    if (auth != null && auth.getPrincipal() instanceof Long userId) {
      count = counter.incrementUser(userId);
      limit = authenticatedLimit;
    } else {
      count = counter.incrementAnonymous(clientIp);
      limit = anonymousLimit;
    }
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
        ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded", "RATE_LIMITED", req);

    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setHeader("Retry-After", "60");
    jsonMapper.writeValue(res.getOutputStream(), body);
  }
}
