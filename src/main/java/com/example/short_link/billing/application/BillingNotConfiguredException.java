package com.example.short_link.billing.application;

public class BillingNotConfiguredException extends RuntimeException {
  public BillingNotConfiguredException() {
    super("billing not configured");
  }
}
