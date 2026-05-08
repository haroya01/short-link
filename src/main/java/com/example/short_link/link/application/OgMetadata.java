package com.example.short_link.link.application;

public record OgMetadata(String title, String description, String image) {

  public static OgMetadata empty() {
    return new OgMetadata(null, null, null);
  }

  public boolean hasAny() {
    return notBlank(title) || notBlank(description) || notBlank(image);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
