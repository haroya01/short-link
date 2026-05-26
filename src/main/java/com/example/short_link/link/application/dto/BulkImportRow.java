package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.ShortCode;

public record BulkImportRow(
    String url, String customCode, String expiresAt, ShortCode shortCode, String error) {

  public BulkImportRow withResult(ShortCode shortCode, String error) {
    return new BulkImportRow(url, customCode, expiresAt, shortCode, error);
  }
}
