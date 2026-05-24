package com.example.short_link.link.exception;

public class ShortCodeGenerationException extends RuntimeException {

  public ShortCodeGenerationException() {
    super("Failed to generate unique short code after multiple attempts");
  }
}
