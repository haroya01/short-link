package com.example.short_link.common.web;

import com.example.short_link.common.pow.PowRequiredException;
import com.example.short_link.common.web.response.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Feature 비관여 공통 예외만 처리 — IllegalArgument / Validation / NoResource / OptimisticLock / PoW gate /
 * catch-all. 각 feature 의 도메인 exception 은 feature 의 @RestControllerAdvice 가 담당
 * (LinkExceptionHandler, CampaignExceptionHandler, ProfileExceptionHandler, UserExceptionHandler,
 * AdminExceptionHandler).
 */
// LOWEST_PRECEDENCE — feature 별 @RestControllerAdvice 가 자기 도메인 exception 을 먼저 잡고, 여기는 catch-all 만
// 처리.
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

  /**
   * IllegalArgumentException is thrown from two very different places — domain-level input checks
   * ("invalid domain", "shortCode required") that legitimately mean 400, and structural code paths
   * (JWT decoding, TOTP base32) that shouldn't surface their internals to a client. We map both to
   * 400 with a generic body so the second category doesn't leak hints; the original message is kept
   * in the log for triage. Long-term: domain checks should throw a dedicated DomainValidation
   * exception so this handler can drop the catch-all entirely.
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

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      OptimisticLockingFailureException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.CONFLICT, "concurrent modification, please retry", "OPTIMISTIC_LOCK", req);
  }

  /**
   * Explicit handler beats the catch-all {@code Exception} handler below — without this,
   * PowRequiredException's 401 would be remapped to 500 and the frontend would lose the signal to
   * clear the expired token and re-shorten with a fresh PoW.
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

  /**
   * @RequestParam / @PathVariable 제약 위반(@Validated 컨트롤러)은 ConstraintViolationException 으로 떨어진다. 전용
   * 핸들러가 없으면 catch-all 이 500 으로 보고해 클라이언트가 입력 오류를 4xx 로 인지하지 못하고 Sentry 에 가짜 에러가 쌓인다.
   * MethodArgumentNotValid 와 동일한 VALIDATION_FAILED / errors 포맷으로 맞춘다.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(
      ConstraintViolationException e, HttpServletRequest req) {
    ProblemDetail body =
        ProblemDetails.of(HttpStatus.BAD_REQUEST, "validation failed", "VALIDATION_FAILED", req);
    List<Map<String, String>> errors =
        e.getConstraintViolations().stream()
            .map(
                v ->
                    Map.of(
                        "field", String.valueOf(v.getPropertyPath()),
                        "message", String.valueOf(v.getMessage())))
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

  /** 업로드 본문이 multipart 한도를 넘으면 발생. catch-all 로 새면 500 이 되지만 의미상 클라이언트 오류이므로 413 으로 매핑한다. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleMaxUploadSize(
      MaxUploadSizeExceededException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.PAYLOAD_TOO_LARGE, "upload too large", "UPLOAD_TOO_LARGE", req);
  }

  /**
   * unique 제약 충돌 등 DB 무결성 위반. 도메인에서 잡지 못하고 빠져나온 경우의 안전망 — 동시 가입/중복 키 race 가 500 이 아닌 409 로 나가도록. 내부
   * 메시지는 노출하지 않고 로그로만 남긴다.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(
      DataIntegrityViolationException e, HttpServletRequest req) {
    log.warn(
        "data integrity violation: {} {} msg={}",
        req.getMethod(),
        req.getRequestURI(),
        e.getMostSpecificCause().getMessage());
    return ProblemDetails.of(HttpStatus.CONFLICT, "conflict", "CONFLICT", req);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnknown(Exception e, HttpServletRequest req) {
    // Method/URI are already in MDC via MdcFilter, but inlining them in the message keeps the
    // admin "recent errors" list scannable without expanding each row, and survives in plain-text
    // logs where MDC isn't part of the visible pattern.
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
