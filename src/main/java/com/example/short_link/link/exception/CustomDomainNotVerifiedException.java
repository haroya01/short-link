package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class CustomDomainNotVerifiedException extends RuntimeException {
  public CustomDomainNotVerifiedException(String domain) {
    super("DNS TXT record for " + domain + " did not match expected token");
  }
}
