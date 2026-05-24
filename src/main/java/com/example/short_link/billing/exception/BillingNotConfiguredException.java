package com.example.short_link.billing.exception;

public class BillingNotConfiguredException extends RuntimeException {
  public BillingNotConfiguredException() {
    super("billing not configured");
  }
}
