package com.example.short_link.user.application;

public class InvalidTimezoneException extends RuntimeException {
  public InvalidTimezoneException(String timezone) {
    super("Invalid timezone: " + timezone);
  }
}
