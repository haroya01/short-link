package com.example.short_link.billing.exception;

import com.example.short_link.common.exception.DomainException;

public abstract sealed class BillingException extends RuntimeException implements DomainException
    permits BillingGatewayException,
        BillingNotConfiguredException,
        BillingNotEnrolledException,
        InvalidWebhookSignatureException {

  protected BillingException(String message) {
    super(message);
  }

  protected BillingException(String message, Throwable cause) {
    super(message, cause);
  }
}
