package com.example.short_link.billing.exception;

import org.springframework.http.HttpStatus;

public final class BillingNotConfiguredException extends BillingException {

  public BillingNotConfiguredException() {
    super("billing not configured");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.SERVICE_UNAVAILABLE;
  }

  @Override
  public String code() {
    return "BILLING_NOT_CONFIGURED";
  }
}
