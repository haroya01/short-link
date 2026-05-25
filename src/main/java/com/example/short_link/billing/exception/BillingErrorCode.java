package com.example.short_link.billing.exception;

import org.springframework.http.HttpStatus;

public enum BillingErrorCode {
  BILLING_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "billing not configured"),
  BILLING_NOT_ENROLLED(HttpStatus.CONFLICT, "user has no Stripe customer yet"),
  INVALID_WEBHOOK_SIGNATURE(HttpStatus.BAD_REQUEST, "invalid Stripe webhook signature"),
  BILLING_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "billing gateway error");

  private final HttpStatus status;
  private final String template;

  BillingErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
