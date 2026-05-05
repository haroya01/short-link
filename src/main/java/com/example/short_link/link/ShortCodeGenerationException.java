package com.example.short_link.link;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ShortCodeGenerationException extends RuntimeException {

  public ShortCodeGenerationException() {
    super("Failed to generate unique short code");
  }
}
