package com.example.short_link.billing.exception;

public class InvalidWebhookSignatureException extends RuntimeException {
  public InvalidWebhookSignatureException() {
    super("invalid Stripe webhook signature");
  }
}
