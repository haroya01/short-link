package com.example.short_link.federation.exception;

import com.example.short_link.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public final class FederationException extends RuntimeException implements DomainException {

  private final FederationErrorCode errorCode;

  public FederationException(FederationErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
  }

  public FederationErrorCode errorCode() {
    return errorCode;
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
