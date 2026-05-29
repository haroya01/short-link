package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

/** One card in the global public feed — a published post plus its author summary. */
public record PublicFeedItem(
    PublicAuthorView author,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String languageTag,
    List<String> tags,
    Instant publishedAt,
    long viewCount) {}
