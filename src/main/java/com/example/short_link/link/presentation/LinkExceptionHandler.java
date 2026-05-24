package com.example.short_link.link.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.link.exception.BulkImportTooLargeException;
import com.example.short_link.link.exception.CustomDomainNotFoundException;
import com.example.short_link.link.exception.CustomDomainNotVerifiedException;
import com.example.short_link.link.exception.DuplicateShortCodeException;
import com.example.short_link.link.exception.DuplicateTagNameException;
import com.example.short_link.link.exception.InvalidCursorException;
import com.example.short_link.link.exception.InvalidExportDimensionException;
import com.example.short_link.link.exception.InvalidWebhookUrlException;
import com.example.short_link.link.exception.LinkExpiredException;
import com.example.short_link.link.exception.LinkNotFoundException;
import com.example.short_link.link.exception.LinkNotOwnedException;
import com.example.short_link.link.exception.LinkQuotaExceededException;
import com.example.short_link.link.exception.LinkViewLimitExceededException;
import com.example.short_link.link.exception.MaliciousUrlException;
import com.example.short_link.link.exception.ReservedShortCodeException;
import com.example.short_link.link.exception.ShortCodeGenerationException;
import com.example.short_link.link.exception.TagNotFoundException;
import com.example.short_link.link.exception.TooManyWebhooksException;
import com.example.short_link.link.exception.WebhookNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LinkExceptionHandler {

  @ExceptionHandler(LinkNotFoundException.class)
  public ProblemDetail handleLinkNotFound(LinkNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "LINK_NOT_FOUND", req);
  }

  @ExceptionHandler(LinkExpiredException.class)
  public ProblemDetail handleLinkExpired(LinkExpiredException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.GONE, e.getMessage(), "LINK_EXPIRED", req);
  }

  @ExceptionHandler(DuplicateShortCodeException.class)
  public ProblemDetail handleDuplicateShortCode(
      DuplicateShortCodeException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage(), "DUPLICATE_SHORT_CODE", req);
  }

  @ExceptionHandler(LinkNotOwnedException.class)
  public ProblemDetail handleLinkNotOwned(LinkNotOwnedException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.FORBIDDEN, e.getMessage(), "LINK_NOT_OWNED", req);
  }

  @ExceptionHandler(MaliciousUrlException.class)
  public ProblemDetail handleMaliciousUrl(MaliciousUrlException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, "url flagged as malicious", "MALICIOUS_URL", req);
  }

  @ExceptionHandler(ReservedShortCodeException.class)
  public ProblemDetail handleReservedShortCode(
      ReservedShortCodeException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "RESERVED_SHORT_CODE", req);
  }

  @ExceptionHandler(LinkQuotaExceededException.class)
  public ProblemDetail handleLinkQuota(LinkQuotaExceededException e, HttpServletRequest req) {
    ProblemDetail body =
        ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage(), "LINK_QUOTA_EXCEEDED", req);
    body.setProperty("limit", e.getLimit());
    return body;
  }

  @ExceptionHandler(LinkViewLimitExceededException.class)
  public ProblemDetail handleViewLimit(LinkViewLimitExceededException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.GONE, e.getMessage(), "LINK_VIEW_LIMIT_EXCEEDED", req);
  }

  @ExceptionHandler(TagNotFoundException.class)
  public ProblemDetail handleTagNotFound(TagNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "TAG_NOT_FOUND", req);
  }

  @ExceptionHandler(DuplicateTagNameException.class)
  public ProblemDetail handleDuplicateTag(DuplicateTagNameException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage(), "DUPLICATE_TAG_NAME", req);
  }

  @ExceptionHandler(InvalidWebhookUrlException.class)
  public ProblemDetail handleInvalidWebhookUrl(
      InvalidWebhookUrlException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_WEBHOOK_URL", req);
  }

  @ExceptionHandler(TooManyWebhooksException.class)
  public ProblemDetail handleTooManyWebhooks(TooManyWebhooksException e, HttpServletRequest req) {
    ProblemDetail body =
        ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage(), "TOO_MANY_WEBHOOKS", req);
    body.setProperty("limit", e.getLimit());
    return body;
  }

  @ExceptionHandler(WebhookNotFoundException.class)
  public ProblemDetail handleWebhookNotFound(WebhookNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "WEBHOOK_NOT_FOUND", req);
  }

  @ExceptionHandler(BulkImportTooLargeException.class)
  public ProblemDetail handleBulkTooLarge(BulkImportTooLargeException e, HttpServletRequest req) {
    ProblemDetail body =
        ProblemDetails.of(
            HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage(), "BULK_IMPORT_TOO_LARGE", req);
    body.setProperty("limit", e.getLimit());
    body.setProperty("rows", e.getRows());
    return body;
  }

  @ExceptionHandler(InvalidCursorException.class)
  public ProblemDetail handleInvalidCursor(InvalidCursorException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_CURSOR", req);
  }

  @ExceptionHandler(InvalidExportDimensionException.class)
  public ProblemDetail handleInvalidExportDimension(
      InvalidExportDimensionException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_EXPORT_DIMENSION", req);
  }

  @ExceptionHandler(CustomDomainNotFoundException.class)
  public ProblemDetail handleCustomDomainNotFound(
      CustomDomainNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "CUSTOM_DOMAIN_NOT_FOUND", req);
  }

  @ExceptionHandler(CustomDomainNotVerifiedException.class)
  public ProblemDetail handleCustomDomainNotVerified(
      CustomDomainNotVerifiedException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), "CUSTOM_DOMAIN_NOT_VERIFIED", req);
  }

  @ExceptionHandler(ShortCodeGenerationException.class)
  public ProblemDetail handleShortCodeExhausted(
      ShortCodeGenerationException e, HttpServletRequest req) {
    log.error("short code generation exhausted", e);
    return ProblemDetails.of(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "short code generation exhausted",
        "SHORT_CODE_EXHAUSTED",
        req);
  }
}
