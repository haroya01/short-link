package com.example.short_link.billing.exception;

import org.springframework.http.HttpStatus;

public final class InvalidWebhookSignatureException extends BillingException {

  public InvalidWebhookSignatureException() {
    super("invalid Stripe webhook signature");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_WEBHOOK_SIGNATURE";
  }
}
