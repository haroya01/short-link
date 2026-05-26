package com.example.short_link.customdomain.exception;

import com.example.short_link.common.exception.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class CustomDomainException extends RuntimeException implements DomainException {

  private final CustomDomainErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public CustomDomainException(CustomDomainErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
  }

  public CustomDomainException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public CustomDomainErrorCode errorCode() {
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
