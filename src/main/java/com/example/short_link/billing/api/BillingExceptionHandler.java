package com.example.short_link.billing.api;

import com.example.short_link.billing.exception.BillingGatewayException;
import com.example.short_link.billing.exception.BillingNotConfiguredException;
import com.example.short_link.billing.exception.BillingNotEnrolledException;
import com.example.short_link.billing.exception.InvalidWebhookSignatureException;
import com.example.short_link.common.api.response.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class BillingExceptionHandler {

  @ExceptionHandler(BillingNotConfiguredException.class)
  public ProblemDetail handleNotConfigured(
      BillingNotConfiguredException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), "BILLING_NOT_CONFIGURED", req);
  }

  @ExceptionHandler(BillingNotEnrolledException.class)
  public ProblemDetail handleNotEnrolled(BillingNotEnrolledException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage(), "BILLING_NOT_ENROLLED", req);
  }

  @ExceptionHandler(InvalidWebhookSignatureException.class)
  public ProblemDetail handleInvalidSignature(
      InvalidWebhookSignatureException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_WEBHOOK_SIGNATURE", req);
  }

  @ExceptionHandler(BillingGatewayException.class)
  public ProblemDetail handleGateway(BillingGatewayException e, HttpServletRequest req) {
    log.warn("billing gateway error", e);
    return ProblemDetails.of(
        HttpStatus.BAD_GATEWAY, "billing gateway error", "BILLING_GATEWAY_ERROR", req);
  }
}
