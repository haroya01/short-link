package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.pow.PowRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void powRequiredReturns401WithStableCode() {
    // Without this explicit handler the catch-all Exception handler would take over and
    // return 500, which breaks the frontend's auto-refresh / retry path. The stable
    // POW_REQUIRED code is what the FE keys off in shortenUrl's recovery branch.
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/links");

    ProblemDetail problem = handler.handlePowRequired(new PowRequiredException(), req);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(problem.getProperties()).containsEntry("code", "POW_REQUIRED");
  }
}
