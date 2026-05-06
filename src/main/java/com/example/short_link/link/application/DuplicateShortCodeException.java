package com.example.short_link.link.application;

public class DuplicateShortCodeException extends RuntimeException {

  public DuplicateShortCodeException(String shortCode) {
    super("short code already exists: " + shortCode);
  }
}
