package com.example.short_link.link.exception;

public class LinkExpiredException extends RuntimeException {

  public LinkExpiredException(String shortCode) {
    super("link expired: " + shortCode);
  }
}
