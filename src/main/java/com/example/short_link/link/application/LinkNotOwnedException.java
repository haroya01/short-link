package com.example.short_link.link.application;

public class LinkNotOwnedException extends RuntimeException {

  public LinkNotOwnedException(String shortCode) {
    super("link not owned by current user: " + shortCode);
  }
}
