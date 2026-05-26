package com.example.short_link.customdomain.exception;

import org.springframework.http.HttpStatus;

public enum CustomDomainErrorCode {
  CUSTOM_DOMAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "custom domain not found"),
  CUSTOM_DOMAIN_NOT_VERIFIED(
      HttpStatus.UNPROCESSABLE_ENTITY, "DNS TXT record for %s did not match expected token");

  private final HttpStatus status;
  private final String template;

  CustomDomainErrorCode(HttpStatus status, String template) {
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
