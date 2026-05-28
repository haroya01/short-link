package com.example.short_link.abuse.presentation;

import com.example.short_link.abuse.exception.AbuseException;
import com.example.short_link.common.web.response.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AbuseExceptionHandler {

  @ExceptionHandler(AbuseException.class)
  public ProblemDetail handle(AbuseException e, HttpServletRequest req) {
    return ProblemDetails.of(e, req);
  }
}
