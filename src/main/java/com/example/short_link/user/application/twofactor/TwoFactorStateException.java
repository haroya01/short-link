package com.example.short_link.user.application.twofactor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TwoFactorStateException extends RuntimeException {
  public TwoFactorStateException(String message) {
    super(message);
  }
}
