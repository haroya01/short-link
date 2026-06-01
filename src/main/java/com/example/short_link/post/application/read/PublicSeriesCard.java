package com.example.short_link.post.application.read;

import java.time.Instant;

/**
 * A series as it appears on the cross-author discovery surface (the feed's series cards): its
 * author, identity, how many published posts it holds, and when the latest one went out (for "마지막 글
 * …").
 */
public record PublicSeriesCard(
    PublicAuthorView author, String slug, String title, int postCount, Instant lastPublishedAt) {}
