package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

public final class InvalidExportDimensionException extends LinkException {

  public InvalidExportDimensionException(String dimension) {
    super("Invalid export dimension: " + dimension);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return "INVALID_EXPORT_DIMENSION";
  }
}
