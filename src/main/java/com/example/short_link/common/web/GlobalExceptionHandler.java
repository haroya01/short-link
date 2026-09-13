package com.example.short_link.common.web;

import com.example.short_link.common.pow.PowRequiredException;
import com.example.short_link.common.web.response.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 도메인 예외는 feature별 advice가 먼저 처리하고, 공통 예외와 catch-all은 여기서 처리한다. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

  /**
   * Return a generic 400 because IllegalArgumentException may expose JWT/TOTP internals as well as
   * ordinary input errors. Keep the original message only in logs.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException e, HttpServletRequest req) {
    log.debug(
        "rejected as invalid argument: {} {} msg={}",
        req.getMethod(),
        req.getRequestURI(),
        e.getMessage());
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "invalid argument", "INVALID_ARGUMENT", req);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleArgumentTypeMismatch(
      MethodArgumentTypeMismatchException e, HttpServletRequest req) {
    ProblemDetail body =
        ProblemDetails.of(HttpStatus.BAD_REQUEST, "invalid parameter", "INVALID_ARGUMENT", req);
    body.setProperty("parameter", e.getName());
    return body;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      OptimisticLockingFailureException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.CONFLICT, "concurrent modification, please retry", "OPTIMISTIC_LOCK", req);
  }

  /**
   * Preserve the 401 signal that tells clients to acquire a fresh PoW; the catch-all would return
   * 500.
   */
  @ExceptionHandler(PowRequiredException.class)
  public ProblemDetail handlePowRequired(PowRequiredException e, HttpServletRequest req) {
    return ProblemDetails.of(e.status(), e.getMessage(), e.code(), req);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, "resource not found", "NOT_FOUND", req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
    ProblemDetail body =
        ProblemDetails.of(HttpStatus.BAD_REQUEST, "validation failed", "VALIDATION_FAILED", req);
    List<Map<String, String>> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    body.setProperty("errors", errors);
    return body;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, "malformed request body", "MALFORMED_REQUEST", req);
  }

  @ExceptionHandler(PayloadTooLargeException.class)
  public ProblemDetail handlePayloadTooLarge(PayloadTooLargeException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.PAYLOAD_TOO_LARGE, "request body too large", "PAYLOAD_TOO_LARGE", req);
  }

  /** SSE 연결 종료로 응답을 쓸 수 없는 상태다. 정상 종료를 에러로 기록하거나 닫힌 응답에 ProblemDetail을 다시 쓰지 않는다. */
  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void handleAsyncRequestNotUsable(
      AsyncRequestNotUsableException e, HttpServletRequest req) {
    log.debug(
        "async response no longer usable (client closed stream): {} {}",
        req.getMethod(),
        req.getRequestURI());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnknown(Exception e, HttpServletRequest req) {
    // Include method/URI because plain-text logs and the admin error list may omit MDC.
    log.error(
        "unexpected error: {} {} ex={}",
        req.getMethod(),
        req.getRequestURI(),
        e.getClass().getSimpleName(),
        e);
    return ProblemDetails.of(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", "INTERNAL_ERROR", req);
  }
}
