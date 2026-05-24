package com.example.short_link.billing.presentation;

import com.example.short_link.billing.exception.BillingException;
import com.example.short_link.billing.exception.BillingGatewayException;
import com.example.short_link.common.web.response.ProblemDetails;
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
public class BillingExceptionHandler {

  @ExceptionHandler(BillingException.class)
  public ProblemDetail handle(BillingException e, HttpServletRequest req) {
    String detail = e.getMessage();
    if (e instanceof BillingGatewayException) {
      log.warn("billing gateway error", e);
      // Hide upstream-specific wording (Stripe error strings) from the response.
      detail = "billing gateway error";
    }
    return ProblemDetails.of(e.status(), detail, e.code(), req);
  }
}
