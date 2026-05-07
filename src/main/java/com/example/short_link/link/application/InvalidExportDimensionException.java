package com.example.short_link.link.application;

public class InvalidExportDimensionException extends RuntimeException {
  public InvalidExportDimensionException(String dimension) {
    super("Invalid export dimension: " + dimension);
  }
}
