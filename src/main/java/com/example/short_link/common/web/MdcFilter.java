package com.example.short_link.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
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

  // A client may pass its own correlation id, but only a safe token — else a crafted value forges
  // log lines (embedded newlines/control chars) or poisons the reflected response header.
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[0-9A-Za-z-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String requestId = sanitizeRequestId(req.getHeader(HEADER_REQUEST_ID));
    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_CLIENT_IP, ClientIp.of(req));
    MDC.put(MDC_METHOD, req.getMethod());
    MDC.put(MDC_URI, req.getRequestURI());
    res.setHeader(HEADER_REQUEST_ID, requestId);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.clear();
    }
  }

  private static String sanitizeRequestId(String headerValue) {
    if (headerValue != null && SAFE_REQUEST_ID.matcher(headerValue).matches()) {
      return headerValue;
    }
    return UUID.randomUUID().toString();
  }
}
