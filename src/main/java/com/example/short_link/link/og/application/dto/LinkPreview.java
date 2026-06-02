package com.example.short_link.link.og.application.dto;

/**
 * Link-preview card for the blog editor + published post link cards: og:title / description / image
 * for an arbitrary URL. Fields are null when the target exposes no Open Graph (the frontend then
 * renders a bare domain card). Lives in {@code com.example.short_link.*} so the polymorphic Redis
 * cache serializer accepts it.
 */
public record LinkPreview(String url, String title, String description, String image) {
  public static LinkPreview bare(String url) {
    return new LinkPreview(url, null, null, null);
  }
}
