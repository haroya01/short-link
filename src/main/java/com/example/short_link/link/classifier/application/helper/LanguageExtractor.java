package com.example.short_link.link.classifier.application.helper;

public final class LanguageExtractor {

  private static final int MAX_LENGTH = 8;

  private LanguageExtractor() {}

  public static String extract(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return null;
    }
    String first = acceptLanguage.split(",", 2)[0].trim();
    int semi = first.indexOf(';');
    if (semi >= 0) {
      first = first.substring(0, semi).trim();
    }
    if (first.isEmpty() || first.equals("*")) {
      return null;
    }
    if (!first.matches("^[A-Za-z]{1,3}(-[A-Za-z0-9]{1,4})?$")) {
      return null;
    }
    return first.length() > MAX_LENGTH ? first.substring(0, MAX_LENGTH) : first;
  }
}
