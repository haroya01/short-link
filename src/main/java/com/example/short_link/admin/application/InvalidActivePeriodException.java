package com.example.short_link.admin.application;

public class InvalidActivePeriodException extends RuntimeException {
  public InvalidActivePeriodException(String period) {
    super("Invalid active-users period: " + period);
  }
}
