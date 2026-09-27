package com.example.short_link.link.redirect.presentation.helper;

import java.util.List;
import java.util.Locale;

public final class VisitorLocale {

  private static final List<Locale> SUPPORTED =
      List.of(
          Locale.KOREAN,
          Locale.ENGLISH,
          Locale.JAPANESE,
          Locale.forLanguageTag("vi"),
          Locale.forLanguageTag("hi"));

  private VisitorLocale() {}

  public static Locale resolve(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return Locale.ENGLISH;
    }
    try {
      Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
      return match == null ? Locale.ENGLISH : match;
    } catch (IllegalArgumentException e) {
      return Locale.ENGLISH;
    }
  }
}
