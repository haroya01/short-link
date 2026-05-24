package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class CustomDomainNotVerifiedException extends LinkException {

  public CustomDomainNotVerifiedException(String domain) {
    super("DNS TXT record for " + domain + " did not match expected token");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }

  @Override
  public String code() {
    return "CUSTOM_DOMAIN_NOT_VERIFIED";
  }
}
