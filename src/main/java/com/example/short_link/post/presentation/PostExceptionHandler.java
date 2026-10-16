package com.example.short_link.post.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.post.exception.PostException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PostExceptionHandler {

  @ExceptionHandler(PostException.class)
  public ProblemDetail handle(PostException e, HttpServletRequest req, HttpServletResponse res) {
    Object retryAfter = e.properties().get("retryAfterSeconds");
    if (retryAfter != null) {
      res.setHeader("Retry-After", String.valueOf(retryAfter));
    }
    return ProblemDetails.of(e, req);
  }
}
