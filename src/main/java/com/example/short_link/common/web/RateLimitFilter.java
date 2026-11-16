package com.example.short_link.common.web;

import com.example.short_link.common.exception.GuardUnavailableException;
import com.example.short_link.common.web.response.ProblemDetails;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  static final String METRIC_NAME = "rate_limit.exceeded";
  static final String UNAVAILABLE_METRIC_NAME = "rate_limit.unavailable";

  // Auth endpoints need stricter per-IP limits to resist brute-forcing six-digit 2FA codes, and
  // the anonymous report endpoints so one address cannot flood the moderation queue.
  private record EndpointRule(
      String method, String path, long perMinute, boolean refusesWhenUncounted) {}

  // dev-login은 dev profile에만 노출되므로 테스트 계정 생성에 별도 인증 제한을 적용하지 않는다.
  private static final List<EndpointRule> ENDPOINT_RULES =
      List.of(
          new EndpointRule("POST", "/api/v1/auth/2fa/verify", 5, true),
          new EndpointRule("POST", "/api/v1/auth/mobile/2fa/verify", 5, true),
          new EndpointRule("POST", "/api/v1/2fa/confirm", 5, true),
          new EndpointRule("POST", "/api/v1/2fa/disable", 5, true),
          new EndpointRule("POST", "/api/v1/2fa/recovery-codes/regenerate", 5, true),
          new EndpointRule("POST", "/api/v1/auth/refresh", 10, true),
          new EndpointRule("POST", "/api/v1/public/abuse-reports", 10, false),
          new EndpointRule("POST", "/api/v1/public/abuse-reports/links", 10, false));

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
    String path = UrlPathHelper.defaultInstance.getPathWithinApplication(req);

    EndpointRule rule = ruleFor(method, path);
    if (rule != null && refusedAtEndpoint(rule, ClientNetwork.of(clientIp), req, res)) {
      return;
    }
    if (refusedOverall(clientIp, req, res)) {
      return;
    }
    chain.doFilter(req, res);
  }

  private static EndpointRule ruleFor(String method, String path) {
    for (EndpointRule rule : ENDPOINT_RULES) {
      if (rule.method().equals(method) && rule.path().equals(path)) {
        return rule;
      }
    }
    return null;
  }

  private boolean refusedAtEndpoint(
      EndpointRule rule, String network, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    Long count;
    try {
      count = counter.incrementEndpoint(rule.method(), rule.path(), network);
    } catch (DataAccessException e) {
      uncounted("endpoint", rule.refusesWhenUncounted(), e);
      if (rule.refusesWhenUncounted()) {
        writeUnavailableResponse(req, res, e);
      }
      return rule.refusesWhenUncounted();
    }
    if (count != null && count > rule.perMinute()) {
      meterRegistry.counter(METRIC_NAME, "scope", "endpoint", "path", rule.path()).increment();
      writeRateLimitResponse(req, res);
      return true;
    }
    return false;
  }

  private boolean refusedOverall(String clientIp, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Long userId = auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    String scope = userId == null ? "anonymous" : "user";
    Long count;
    try {
      count = userId == null ? counter.incrementAnonymous(clientIp) : counter.incrementUser(userId);
    } catch (DataAccessException e) {
      uncounted(scope, false, e);
      return false;
    }
    long limit = userId == null ? anonymousLimit : authenticatedLimit;
    if (count != null && count > limit) {
      meterRegistry.counter(METRIC_NAME, "scope", scope).increment();
      writeRateLimitResponse(req, res);
      return true;
    }
    return false;
  }

  private void uncounted(String scope, boolean refused, DataAccessException e) {
    meterRegistry
        .counter(UNAVAILABLE_METRIC_NAME, "scope", scope, "result", refused ? "refused" : "passed")
        .increment();
    log.warn(
        "rate limit counter unavailable, {} request {}: {}",
        scope,
        refused ? "refused" : "passed",
        e.getMessage());
  }

  private void writeUnavailableResponse(
      HttpServletRequest req, HttpServletResponse res, DataAccessException cause)
      throws IOException {
    GuardUnavailableException unavailable = new GuardUnavailableException(cause);
    res.setStatus(unavailable.status().value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setHeader("Retry-After", String.valueOf(GuardUnavailableException.RETRY_AFTER_SECONDS));
    jsonMapper.writeValue(res.getOutputStream(), ProblemDetails.of(unavailable, req));
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
