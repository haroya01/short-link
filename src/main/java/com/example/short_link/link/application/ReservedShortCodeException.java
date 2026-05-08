package com.example.short_link.link.application;

public class ReservedShortCodeException extends RuntimeException {

  public ReservedShortCodeException(String code) {
    super("short code is reserved: " + code);
  }
}
