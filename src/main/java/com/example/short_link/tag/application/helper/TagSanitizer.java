package com.example.short_link.tag.application.helper;

public final class TagSanitizer {

  public static final int MAX_NAME_LENGTH = 50;
  public static final int MAX_TAGS_PER_LINK = 20;

  private TagSanitizer() {}

  public static String sanitizeName(String name) {
    if (name == null) throw new IllegalArgumentException("tag name required");
    String trimmed = name.trim();
    if (trimmed.isEmpty()) throw new IllegalArgumentException("tag name required");
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("tag name too long (max " + MAX_NAME_LENGTH + ")");
    }
    return trimmed;
  }

  public static String sanitizeColor(String color) {
    if (color == null) return null;
    String trimmed = color.trim();
    if (trimmed.isEmpty()) return null;
    if (!trimmed.matches("^#[0-9a-fA-F]{6}$")) {
      throw new IllegalArgumentException("color must be #RRGGBB");
    }
    return trimmed.toLowerCase();
  }
}
