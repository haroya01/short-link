package com.example.short_link.event.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.event.exception.EventException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EventExceptionHandler {

  @ExceptionHandler(EventException.class)
  public ProblemDetail handle(EventException e, HttpServletRequest req) {
    return ProblemDetails.of(e, req);
  }
}
