package com.example.short_link.link.exception;

public class TagNotFoundException extends RuntimeException {

  public TagNotFoundException(Long id) {
    super("tag not found: " + id);
  }
}
