package com.example.short_link.billing.exception;

import com.example.short_link.common.exception.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class BillingException extends RuntimeException implements DomainException {

  private final BillingErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public BillingException(BillingErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
  }

  public BillingException(BillingErrorCode errorCode, Throwable cause) {
    super(errorCode.format(), cause);
    this.errorCode = errorCode;
  }

  public BillingException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public BillingErrorCode errorCode() {
    return errorCode;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  @Override
  public HttpStatus status() {
    return errorCode.status();
  }

  @Override
  public String code() {
    return errorCode.name();
  }
}
