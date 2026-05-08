package com.example.short_link.user.application.twofactor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTotpCodeException extends RuntimeException {
  public InvalidTotpCodeException() {
    super("invalid TOTP or recovery code");
  }
}
