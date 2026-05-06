package com.example.short_link.link.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ShortCodeGenerationException extends RuntimeException {

  public ShortCodeGenerationException() {
    super("Failed to generate unique short code after multiple attempts");
  }
}
