package com.example.short_link.link.og.application.dto;

/**
 * Missing Open Graph fields are null. Keep this type under {@code com.example.short_link.*} so the
 * polymorphic Redis serializer accepts it.
 */
public record LinkPreview(String url, String title, String description, String image) {
  public static LinkPreview bare(String url) {
    return new LinkPreview(url, null, null, null);
  }
}
