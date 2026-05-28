package com.example.short_link.link.domain;

final class LinkText {

  static final int NOTE_MAX_LENGTH = 280;
  static final int EXPIRED_MESSAGE_MAX_LENGTH = 500;

  private LinkText() {}

  static String normalize(String value, int maxLength) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      return null;
    }
    return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  static String firstNonBlank(String preferred, String fallback) {
    return preferred != null && !preferred.isBlank() ? preferred : fallback;
  }
}
