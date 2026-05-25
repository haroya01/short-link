package com.example.short_link.link.application.dto;

public record BulkImportRow(
    String url, String customCode, String expiresAt, String shortCode, String error) {

  public BulkImportRow withResult(String shortCode, String error) {
    return new BulkImportRow(url, customCode, expiresAt, shortCode, error);
  }
}
