package com.example.short_link.common.api;

import com.example.short_link.link.application.DuplicateShortCodeException;
import com.example.short_link.link.application.LinkExpiredException;
import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.application.ShortCodeGenerationException;
import com.example.short_link.user.application.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(LinkNotFoundException.class)
  public ProblemDetail handleLinkNotFound(LinkNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "LINK_NOT_FOUND", req);
  }

  @ExceptionHandler(LinkExpiredException.class)
  public ProblemDetail handleLinkExpired(LinkExpiredException e, HttpServletRequest req) {
    return problem(HttpStatus.GONE, e.getMessage(), "LINK_EXPIRED", req);
  }

  @ExceptionHandler(DuplicateShortCodeException.class)
  public ProblemDetail handleDuplicateShortCode(
      DuplicateShortCodeException e, HttpServletRequest req) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), "DUPLICATE_SHORT_CODE", req);
  }

  @ExceptionHandler(LinkNotOwnedException.class)
  public ProblemDetail handleLinkNotOwned(LinkNotOwnedException e, HttpServletRequest req) {
    return problem(HttpStatus.FORBIDDEN, e.getMessage(), "LINK_NOT_OWNED", req);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      OptimisticLockingFailureException e, HttpServletRequest req) {
    return problem(
        HttpStatus.CONFLICT, "concurrent modification, please retry", "OPTIMISTIC_LOCK", req);
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ProblemDetail handleInvalidRefresh(
      InvalidRefreshTokenException e, HttpServletRequest req) {
    return problem(HttpStatus.UNAUTHORIZED, e.getMessage(), "INVALID_REFRESH_TOKEN", req);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, "resource not found", "NOT_FOUND", req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
    ProblemDetail body =
        problem(HttpStatus.BAD_REQUEST, "validation failed", "VALIDATION_FAILED", req);
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
    return problem(HttpStatus.BAD_REQUEST, "malformed request body", "MALFORMED_REQUEST", req);
  }

  @ExceptionHandler(ShortCodeGenerationException.class)
  public ProblemDetail handleShortCodeExhausted(
      ShortCodeGenerationException e, HttpServletRequest req) {
    log.error("short code generation exhausted", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "short code generation exhausted",
        "SHORT_CODE_EXHAUSTED",
        req);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnknown(Exception e, HttpServletRequest req) {
    log.error("unexpected error", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", "INTERNAL_ERROR", req);
  }

  private ProblemDetail problem(
      HttpStatus status, String detail, String code, HttpServletRequest req) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setInstance(URI.create(req.getRequestURI()));
    p.setProperty("code", code);
    p.setProperty("timestamp", Instant.now().toString());
    return p;
  }
}
