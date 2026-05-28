package com.example.short_link.post.exception;

import com.example.short_link.common.exception.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class PostException extends RuntimeException implements DomainException {

  private final PostErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public PostException(PostErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
  }

  public PostException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public PostErrorCode errorCode() {
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
