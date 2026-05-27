package com.example.short_link.customdomain.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.customdomain.exception.CustomDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomDomainExceptionHandler {

  @ExceptionHandler(CustomDomainException.class)
  public ProblemDetail handle(CustomDomainException e, HttpServletRequest req) {
    return ProblemDetails.of(e, req);
  }
}
