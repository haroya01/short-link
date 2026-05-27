package com.example.short_link.tag.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.tag.exception.TagException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TagExceptionHandler {

  @ExceptionHandler(TagException.class)
  public ProblemDetail handle(TagException e, HttpServletRequest req) {
    return ProblemDetails.of(e, req);
  }
}
