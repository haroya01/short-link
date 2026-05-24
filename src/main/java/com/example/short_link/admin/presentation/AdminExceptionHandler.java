package com.example.short_link.admin.presentation;

import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.common.web.response.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {

  @ExceptionHandler(AdminException.class)
  public ProblemDetail handle(AdminException e, HttpServletRequest req) {
    ProblemDetail body = ProblemDetails.of(e.status(), e.getMessage(), e.code(), req);
    e.properties().forEach(body::setProperty);
    return body;
  }
}
