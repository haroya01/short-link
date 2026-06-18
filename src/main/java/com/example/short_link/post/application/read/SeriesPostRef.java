package com.example.short_link.post.application.read;

/**
 * A minimal reference to a series member post — just enough to list + link it in a series card.
 * {@code ogImageUrl} (nullable) lets the discovery card render a photo cover for that episode.
 */
public record SeriesPostRef(String slug, String title, String ogImageUrl) {}
