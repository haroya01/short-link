package com.example.short_link.link.webhook.exception;

import com.example.short_link.common.exception.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class WebhookException extends RuntimeException implements DomainException {

  private final WebhookErrorCode errorCode;
  private final Map<String, Object> properties = new LinkedHashMap<>();

  public WebhookException(WebhookErrorCode errorCode, Object... messageArgs) {
    super(errorCode.format(messageArgs));
    this.errorCode = errorCode;
    String[] keys = errorCode.metadataKeys();
    if (messageArgs != null) {
      for (int i = 0; i < keys.length && i < messageArgs.length; i++) {
        properties.put(keys[i], messageArgs[i]);
      }
    }
  }

  public WebhookException with(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  public WebhookErrorCode errorCode() {
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
