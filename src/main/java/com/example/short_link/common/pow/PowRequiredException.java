package com.example.short_link.common.pow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class PowRequiredException extends RuntimeException {
  public PowRequiredException() {
    super("proof-of-work required for anonymous shorten");
  }
}
