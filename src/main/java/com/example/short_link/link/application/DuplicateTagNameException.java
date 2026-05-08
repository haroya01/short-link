package com.example.short_link.link.application;

public class DuplicateTagNameException extends RuntimeException {

  public DuplicateTagNameException(String name) {
    super("tag already exists: " + name);
  }
}
