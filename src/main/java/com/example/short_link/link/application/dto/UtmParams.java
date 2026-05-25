package com.example.short_link.link.application.dto;

public record UtmParams(
    String source, String medium, String campaign, String term, String content) {

  public static UtmParams empty() {
    return new UtmParams(null, null, null, null, null);
  }
}
