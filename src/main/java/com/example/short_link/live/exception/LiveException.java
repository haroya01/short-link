package com.example.short_link.live.exception;

import com.example.short_link.common.exception.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class LiveException extends RuntimeException implements DomainException {

  private final LiveErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public LiveException(LiveErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
  }

  public LiveException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public LiveErrorCode errorCode() {
    return errorCode;
  }

  @Override
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
