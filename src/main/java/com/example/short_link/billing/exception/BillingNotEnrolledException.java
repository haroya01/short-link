package com.example.short_link.billing.exception;

import org.springframework.http.HttpStatus;

public final class BillingNotEnrolledException extends BillingException {

  public BillingNotEnrolledException() {
    super("user has no Stripe customer yet");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return "BILLING_NOT_ENROLLED";
  }
}
