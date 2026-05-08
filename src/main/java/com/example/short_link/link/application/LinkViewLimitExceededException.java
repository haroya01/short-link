package com.example.short_link.link.application;

public class LinkViewLimitExceededException extends RuntimeException {

  public LinkViewLimitExceededException(String shortCode) {
    super("link view limit exceeded: " + shortCode);
  }
}
