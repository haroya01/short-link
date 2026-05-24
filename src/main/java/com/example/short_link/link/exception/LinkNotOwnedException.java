package com.example.short_link.link.exception;

public class LinkNotOwnedException extends RuntimeException {

  public LinkNotOwnedException(String shortCode) {
    super("link not owned by current user: " + shortCode);
  }
}
