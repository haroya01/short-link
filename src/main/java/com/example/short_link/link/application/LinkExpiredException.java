package com.example.short_link.link.application;

public class LinkExpiredException extends RuntimeException {

  public LinkExpiredException(String shortCode) {
    super("link expired: " + shortCode);
  }
}
