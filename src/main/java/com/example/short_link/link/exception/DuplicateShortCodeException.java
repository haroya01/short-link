package com.example.short_link.link.exception;

public class DuplicateShortCodeException extends RuntimeException {

  public DuplicateShortCodeException(String shortCode) {
    super("short code already exists: " + shortCode);
  }
}
