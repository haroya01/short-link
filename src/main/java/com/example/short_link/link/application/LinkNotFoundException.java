package com.example.short_link.link.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class LinkNotFoundException extends RuntimeException {

  public LinkNotFoundException(String shortCode) {
    super("link not found: " + shortCode);
  }
}
