package com.example.short_link.profile.oembed;

/**
 * Trimmed oembed response we return to the frontend. The provider's raw JSON has many more fields
 * (cache_age, author_url, etc.); we keep only what renders. {@code html} is the provider's iframe
 * snippet passed through as-is — safe because the provider is whitelisted in {@link EmbedProvider}
 * and the response is cached, so the trust boundary is the provider, not the user-supplied URL.
 */
public record OembedResponse(
    String provider,
    String type,
    String title,
    String authorName,
    String thumbnailUrl,
    String html,
    Integer width,
    Integer height) {

  public static OembedResponse empty(String provider) {
    return new OembedResponse(provider, null, null, null, null, null, null, null);
  }
}
