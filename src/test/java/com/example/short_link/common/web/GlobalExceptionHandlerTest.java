package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.pow.PowRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

  @Test
  void constraintViolationReturns400NotCaughtBy500() {
    // @RequestParam / @PathVariable 제약 위반은 ConstraintViolationException 으로 떨어진다. 전용 핸들러가
    // 없으면 catch-all 이 500 으로 보고하던 자리 — 400 VALIDATION_FAILED 로 매핑되어야 한다.
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/public/oembed");

    ProblemDetail problem =
        handler.handleConstraintViolation(
            new ConstraintViolationException("validation failed", Set.of()), req);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
    assertThat(problem.getProperties()).containsKey("errors");
  }

  @Test
  void maxUploadSizeReturns413() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/links/import");

    ProblemDetail problem =
        handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1024L), req);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    assertThat(problem.getProperties()).containsEntry("code", "UPLOAD_TOO_LARGE");
  }

  @Test
  void dataIntegrityViolationReturns409NotCaughtBy500() {
    // unique 제약 충돌 등이 도메인을 빠져나오면 catch-all 이 500 으로 보고하던 자리 — 409 CONFLICT 안전망.
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/users/me/profile");

    ProblemDetail problem =
        handler.handleDataIntegrity(
            new DataIntegrityViolationException("duplicate key value"), req);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getProperties()).containsEntry("code", "CONFLICT");
  }
}
