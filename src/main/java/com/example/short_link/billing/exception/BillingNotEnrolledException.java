package com.example.short_link.billing.exception;

public class BillingNotEnrolledException extends RuntimeException {
  public BillingNotEnrolledException() {
    super("user has no Stripe customer yet");
  }
}
