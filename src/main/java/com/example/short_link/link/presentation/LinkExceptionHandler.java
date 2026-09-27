package com.example.short_link.link.presentation;

import com.example.short_link.common.web.response.ProblemDetails;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LinkExceptionHandler {

  @ExceptionHandler(LinkException.class)
  public ProblemDetail handle(LinkException e, HttpServletRequest req) {
    if (e.errorCode() == LinkErrorCode.SHORT_CODE_EXHAUSTED) {
      log.error("short code generation exhausted", e);
    }
    return ProblemDetails.of(e, req);
  }
}
