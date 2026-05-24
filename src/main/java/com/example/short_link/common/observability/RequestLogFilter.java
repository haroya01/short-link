package com.example.short_link.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs one INFO line per HTTP request at completion with method, uri, status and latency. Sits just
 * inside {@link com.example.short_link.common.web.MdcFilter} so the line carries requestId and
 * userId via MDC. Skips actuator/health/redirect probe paths to keep the volume sane — those are
 * already covered by Micrometer metrics.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class RequestLogFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    String uri = req.getRequestURI();
    if (uri == null) return false;
    return uri.startsWith("/actuator")
        || uri.equals("/healthz")
        || uri.equals("/favicon.ico")
        || uri.startsWith("/.well-known");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    Throwable failure = null;
    try {
      chain.doFilter(req, res);
    } catch (IOException | ServletException | RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      long latencyMs = (System.nanoTime() - start) / 1_000_000L;
      int status = res.getStatus();
      if (failure != null) {
        log.warn(
            "request failed: {} {} status={} latency={}ms err={}",
            req.getMethod(),
            req.getRequestURI(),
            status,
            latencyMs,
            failure.getClass().getSimpleName());
      } else if (status >= 500) {
        log.warn(
            "request 5xx: {} {} status={} latency={}ms",
            req.getMethod(),
            req.getRequestURI(),
            status,
            latencyMs);
      } else {
        log.info(
            "request: {} {} status={} latency={}ms",
            req.getMethod(),
            req.getRequestURI(),
            status,
            latencyMs);
      }
    }
  }
}
