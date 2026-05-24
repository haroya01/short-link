package com.example.short_link.admin.api;

import com.example.short_link.admin.exception.InvalidActivePeriodException;
import com.example.short_link.common.api.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionHandler {

  @ExceptionHandler(InvalidActivePeriodException.class)
  public ProblemDetail handleInvalidActivePeriod(
      InvalidActivePeriodException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ACTIVE_PERIOD", req);
  }
}
