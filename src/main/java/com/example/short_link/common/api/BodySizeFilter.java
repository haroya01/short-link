package com.example.short_link.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BodySizeFilter extends OncePerRequestFilter {

  private static final long MAX_BODY_BYTES = 16L * 1024L;

  private final ObjectMapper objectMapper;

  public BodySizeFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long contentLength = req.getContentLengthLong();
    if (contentLength > MAX_BODY_BYTES) {
      writeTooLarge(req, res);
      return;
    }
    chain.doFilter(req, res);
  }

  private void writeTooLarge(HttpServletRequest req, HttpServletResponse res) throws IOException {
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE, "request body exceeds 16KB limit");
    body.setInstance(URI.create(req.getRequestURI()));
    body.setProperty("code", "PAYLOAD_TOO_LARGE");
    body.setProperty("timestamp", Instant.now().toString());
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      body.setProperty("requestId", requestId);
    }

    res.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(res.getOutputStream(), body);
  }
}
