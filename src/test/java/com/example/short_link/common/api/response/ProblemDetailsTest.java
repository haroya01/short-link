package com.example.short_link.common.api.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailsTest {

  private HttpServletRequest req;

  @BeforeEach
  void setUp() {
    req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/links");
  }

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  void carriesStatusCodeInstanceAndTimestamp() {
    ProblemDetail p = ProblemDetails.of(HttpStatus.NOT_FOUND, "missing", "LINK_NOT_FOUND", req);
    assertThat(p.getStatus()).isEqualTo(404);
    assertThat(p.getDetail()).isEqualTo("missing");
    assertThat(p.getInstance().toString()).isEqualTo("/api/v1/links");
    assertThat(p.getProperties()).containsEntry("code", "LINK_NOT_FOUND");
    assertThat(p.getProperties()).containsKey("timestamp");
  }

  @Test
  void requestIdInjectedWhenMdcPresent() {
    MDC.put("requestId", "req-7");
    ProblemDetail p = ProblemDetails.of(HttpStatus.BAD_REQUEST, "x", "X", req);
    assertThat(p.getProperties()).containsEntry("requestId", "req-7");
  }

  @Test
  void requestIdOmittedWhenMdcEmpty() {
    ProblemDetail p = ProblemDetails.of(HttpStatus.BAD_REQUEST, "x", "X", req);
    assertThat(p.getProperties()).doesNotContainKey("requestId");
  }
}
