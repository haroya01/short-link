package com.example.short_link.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Records one {@link RequestMetric} per finished request and hands it off to the buffered async
 * writer. Sits low in the filter chain so the recorded {@code route} reflects the matched
 * {@code @RequestMapping} pattern (filled in by Spring after dispatch), not the raw URI — without
 * that we'd group every {@code /r/abc} / {@code /r/xyz} hit as its own route and blow up the
 * dashboard's row count.
 *
 * <p>Some endpoints are filtered out: the actuator path (noisy, never useful), and the metrics read
 * endpoint itself (recursive — an admin opening the dashboard would log itself N times).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestMetricsFilter extends OncePerRequestFilter {

  private static final String ACTUATOR_PREFIX = "/actuator";
  private static final String METRICS_PREFIX = "/api/v1/admin/metrics/";

  private final RequestMetricsRecorder recorder;
  private final Clock clock;

  @Autowired
  public RequestMetricsFilter(RequestMetricsRecorder recorder) {
    this(recorder, Clock.systemUTC());
  }

  RequestMetricsFilter(RequestMetricsRecorder recorder, Clock clock) {
    this.recorder = recorder;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null && (uri.startsWith(ACTUATOR_PREFIX) || uri.startsWith(METRICS_PREFIX));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long startNanos = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
      recorder.record(buildMetric(request, response.getStatus(), latencyMs));
    }
  }

  private RequestMetric buildMetric(HttpServletRequest request, int status, long latencyMs) {
    String route = resolveRoute(request);
    String outcome = OutcomeResolver.resolve(request, status);
    String shortCode = extractShortCode(request);
    Long userId = extractUserId();
    String traceId = MDC.get("traceId");
    return new RequestMetric(
        Instant.now(clock),
        route,
        request.getMethod(),
        status,
        outcome,
        latencyMs,
        shortCode,
        userId,
        traceId);
  }

  private static String resolveRoute(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern instanceof String s && !s.isBlank()) return s;
    String uri = request.getRequestURI();
    return uri == null || uri.isBlank() ? "/" : uri;
  }

  @SuppressWarnings("unchecked")
  private static String extractShortCode(HttpServletRequest request) {
    Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(vars instanceof Map<?, ?> map)) return null;
    Object code = ((Map<String, String>) map).get("shortCode");
    if (code instanceof String s && !s.isBlank()) return s;
    return null;
  }

  private static Long extractUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return null;
    Object principal = auth.getPrincipal();
    return principal instanceof Long l ? l : null;
  }
}
