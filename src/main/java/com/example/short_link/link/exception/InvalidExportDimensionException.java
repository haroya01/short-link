package com.example.short_link.link.exception;

public class InvalidExportDimensionException extends RuntimeException {
  public InvalidExportDimensionException(String dimension) {
    super("Invalid export dimension: " + dimension);
  }
}
