package com.example.short_link.link.webhook.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.link.webhook.exception.WebhookException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebhookExceptionHandler {

  @ExceptionHandler(WebhookException.class)
  public ProblemDetail handle(WebhookException e, HttpServletRequest req) {
    ProblemDetail body = ProblemDetails.of(e.status(), e.getMessage(), e.code(), req);
    e.properties().forEach(body::setProperty);
    return body;
  }
}
