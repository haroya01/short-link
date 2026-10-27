package com.example.short_link.common.note;

// A note's few words in lists and notifications: whitespace folded, at most 80 code points and an
// ellipsis after. A warned note is listed by its warning, never by the text the warning hides.
public final class Excerpt {

  static final int LENGTH = 80;

  private Excerpt() {}

  public static String of(String text) {
    String flat = text == null ? "" : text.strip().replaceAll("\\s+", " ");
    if (flat.codePointCount(0, flat.length()) <= LENGTH) {
      return flat;
    }
    return flat.substring(0, flat.offsetByCodePoints(0, LENGTH)) + "…";
  }

  public static String of(String body, String warning) {
    return of(warning != null && !warning.isBlank() ? warning : body);
  }
}
