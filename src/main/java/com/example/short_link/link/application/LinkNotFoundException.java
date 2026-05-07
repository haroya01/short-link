package com.example.short_link.link.application;

public class LinkNotFoundException extends RuntimeException {

  public LinkNotFoundException(String shortCode) {
    super("link not found: " + shortCode);
  }
}
