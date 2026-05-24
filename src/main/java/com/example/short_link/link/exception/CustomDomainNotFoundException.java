package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class CustomDomainNotFoundException extends LinkException {

  public CustomDomainNotFoundException() {
    super("custom domain not found");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return "CUSTOM_DOMAIN_NOT_FOUND";
  }
}
