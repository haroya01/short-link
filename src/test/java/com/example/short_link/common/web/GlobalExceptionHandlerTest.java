package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.pow.PowRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

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
  void aResponseStatusExceptionKeepsItsClientStatusAndReason() {
    ResponseEntity<ProblemDetail> response =
        handler.handleErrorResponse(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown feed tab: x"), request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getDetail()).isEqualTo("Unknown feed tab: x");
    assertThat(response.getBody().getProperties()).containsEntry("code", "BAD_REQUEST");
  }

  @Test
  void aResponseStatusExceptionWithoutReasonUsesTheStatusPhrase() {
    ResponseEntity<ProblemDetail> response =
        handler.handleErrorResponse(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED), request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().getDetail()).isEqualTo("Unauthorized");
    assertThat(response.getBody().getProperties()).containsEntry("code", "UNAUTHORIZED");
  }

  @Test
  void aServerSideResponseStatusExceptionStaysAnInternalError() {
    ResponseEntity<ProblemDetail> response =
        handler.handleErrorResponse(
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
  }

  @Test
  void anUploadOverTheMultipartLimitIs413() {
    ProblemDetail problem =
        handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1_048_576L), request());

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    assertThat(problem.getProperties()).containsEntry("code", "PAYLOAD_TOO_LARGE");
  }

  @Test
  void aMultipartBodyThatCannotBeParsedIs400() {
    ProblemDetail problem =
        handler.handleMalformedMultipart(
            new MultipartException("Current request is not a multipart request"), request());

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getProperties()).containsEntry("code", "MALFORMED_REQUEST");
  }

  private static HttpServletRequest request() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/feed/prefs");
    when(req.getMethod()).thenReturn("PUT");
    return req;
  }
}
