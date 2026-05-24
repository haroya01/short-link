package com.example.short_link.user.presentation;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  public static final String METRIC_NAME = "auth.failure";

  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;

  @Override
  public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
      throws IOException {
    meterRegistry.counter(METRIC_NAME).increment();
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "authentication required");
    body.setInstance(URI.create(req.getRequestURI()));
    body.setProperty("code", "UNAUTHORIZED");
    body.setProperty("timestamp", Instant.now().toString());
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      body.setProperty("requestId", requestId);
    }

    res.setStatus(HttpStatus.UNAUTHORIZED.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(res.getOutputStream(), body);
  }
}
