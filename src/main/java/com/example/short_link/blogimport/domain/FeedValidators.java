package com.example.short_link.blogimport.domain;

public record FeedValidators(String etag, String lastModified) {

  public static final FeedValidators NONE = new FeedValidators(null, null);

  public FeedValidators {
    etag = blankToNull(etag, 255);
    lastModified = blankToNull(lastModified, 64);
  }

  public boolean isEmpty() {
    return etag == null && lastModified == null;
  }

  private static String blankToNull(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) {
      return null;
    }
    return value.strip();
  }
}
