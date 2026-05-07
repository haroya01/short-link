package com.example.short_link.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

  private static final String HEADER_REQUEST_ID = "X-Request-Id";
  private static final String MDC_REQUEST_ID = "requestId";
  private static final String MDC_CLIENT_IP = "clientIp";
  private static final String MDC_METHOD = "method";
  private static final String MDC_URI = "uri";

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String requestId = req.getHeader(HEADER_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_CLIENT_IP, clientIp(req));
    MDC.put(MDC_METHOD, req.getMethod());
    MDC.put(MDC_URI, req.getRequestURI());
    res.setHeader(HEADER_REQUEST_ID, requestId);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.clear();
    }
  }

  private String clientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
