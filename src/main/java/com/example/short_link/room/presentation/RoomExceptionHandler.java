package com.example.short_link.room.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.room.exception.RoomException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoomExceptionHandler {

  @ExceptionHandler(RoomException.class)
  public ProblemDetail handle(RoomException e, HttpServletRequest req) {
    return ProblemDetails.of(e, req);
  }
}
