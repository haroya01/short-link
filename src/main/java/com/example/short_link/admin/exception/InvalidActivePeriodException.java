package com.example.short_link.admin.exception;

import org.springframework.http.HttpStatus;

public final class InvalidActivePeriodException extends AdminException {

  public InvalidActivePeriodException(String period) {
    super("Invalid active-users period: " + period);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_ACTIVE_PERIOD";
  }
}
