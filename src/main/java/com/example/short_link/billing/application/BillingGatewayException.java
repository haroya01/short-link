package com.example.short_link.billing.application;

public class BillingGatewayException extends RuntimeException {

  public BillingGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
