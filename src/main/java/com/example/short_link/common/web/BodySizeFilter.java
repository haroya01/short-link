package com.example.short_link.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class BodySizeFilter extends OncePerRequestFilter {

  private static final long DEFAULT_MAX_BODY_BYTES = 16L * 1024L;

  /**
   * Routes whose legitimate payloads outgrow the default cap: the block editor saves whole
   * documents (per-block validation alone allows far more than 16KB), bulk import uploads CSV
   * files, and Stripe webhook events can run large — a 413 there makes Stripe retry and eventually
   * disable the endpoint.
   */
  private static final List<Limit> EXPANDED_LIMITS =
      List.of(
          new Limit("/api/v1/posts", 1024L * 1024L),
          new Limit("/api/v1/links/bulk", 1024L * 1024L),
          new Limit("/api/v1/billing/webhook", 256L * 1024L));

  private record Limit(String pathPrefix, long maxBytes) {}

  private final JsonMapper jsonMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long contentLength = req.getContentLengthLong();
    long limit = limitFor(req.getRequestURI());
    if (contentLength > limit) {
      writeTooLarge(req, res, limit);
      return;
    }
    chain.doFilter(req, res);
  }

  private static long limitFor(String requestUri) {
    for (Limit limit : EXPANDED_LIMITS) {
      if (requestUri.startsWith(limit.pathPrefix())) {
        return limit.maxBytes();
      }
    }
    return DEFAULT_MAX_BODY_BYTES;
  }

  private void writeTooLarge(HttpServletRequest req, HttpServletResponse res, long limit)
      throws IOException {
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE, "request body exceeds " + (limit / 1024) + "KB limit");
    body.setInstance(URI.create(req.getRequestURI()));
    body.setProperty("code", "PAYLOAD_TOO_LARGE");
    body.setProperty("timestamp", Instant.now().toString());
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      body.setProperty("requestId", requestId);
    }

    res.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(res.getOutputStream(), body);
  }
}
