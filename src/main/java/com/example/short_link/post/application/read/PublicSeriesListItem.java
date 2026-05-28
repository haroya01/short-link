package com.example.short_link.post.application.read;

/** Public series summary for the author's series index. postCount counts published members only. */
public record PublicSeriesListItem(String slug, String title, int postCount) {}
