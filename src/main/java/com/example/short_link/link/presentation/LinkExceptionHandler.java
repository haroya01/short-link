package com.example.short_link.link.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.link.exception.BulkImportTooLargeException;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.exception.LinkQuotaExceededException;
import com.example.short_link.link.exception.MaliciousUrlException;
import com.example.short_link.link.exception.ShortCodeGenerationException;
import com.example.short_link.link.exception.TooManyWebhooksException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single handler for the entire {@link LinkException} sealed hierarchy. status() / code() come from
 * the exception itself; this advice only customizes the message for exceptions that hide details
 * (e.g. malicious URL) and attaches metadata for exceptions that carry it (quota, webhook limit,
 * bulk import). Adding a new link exception that needs no extra metadata works without touching
 * this file.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LinkExceptionHandler {

  @ExceptionHandler(LinkException.class)
  public ProblemDetail handle(LinkException e, HttpServletRequest req) {
    String detail =
        (e instanceof MaliciousUrlException) ? "url flagged as malicious" : e.getMessage();
    ProblemDetail body = ProblemDetails.of(e.status(), detail, e.code(), req);
    if (e instanceof LinkQuotaExceededException q) {
      body.setProperty("limit", q.getLimit());
    } else if (e instanceof TooManyWebhooksException w) {
      body.setProperty("limit", w.getLimit());
    } else if (e instanceof BulkImportTooLargeException b) {
      body.setProperty("limit", b.getLimit());
      body.setProperty("rows", b.getRows());
    } else if (e instanceof ShortCodeGenerationException) {
      log.error("short code generation exhausted", e);
    }
    return body;
  }
}
