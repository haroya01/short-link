package com.example.short_link.billing.exception;

import org.springframework.http.HttpStatus;

public final class BillingGatewayException extends BillingException {

  public BillingGatewayException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_GATEWAY;
  }

  @Override
  public String code() {
    return "BILLING_GATEWAY_ERROR";
  }
}
