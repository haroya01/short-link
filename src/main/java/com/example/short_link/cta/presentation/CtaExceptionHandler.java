package com.example.short_link.cta.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.cta.exception.CtaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CtaExceptionHandler {

  @ExceptionHandler(CtaException.class)
  public ProblemDetail handle(CtaException e, HttpServletRequest req) {
    return ProblemDetails.of(e, req);
  }
}
