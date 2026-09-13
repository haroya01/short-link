package com.example.short_link.profile.application.oembed;

/** {@code html} is passed through unchanged; the allow-listed provider is the trust boundary. */
public record OembedMetadata(
    String provider,
    String type,
    String title,
    String authorName,
    String thumbnailUrl,
    String html,
    Integer width,
    Integer height) {

  public static OembedMetadata empty(String provider) {
    return new OembedMetadata(provider, null, null, null, null, null, null, null);
  }
}
