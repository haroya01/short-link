package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

/**
 * A series as it appears on the cross-author discovery surface (the feed's series cards): its
 * author, identity, how many published posts it holds, when the latest one went out (for "마지막 글
 * …"), and a short preview of its first published members so the card can show what's inside
 * without a click.
 */
public record PublicSeriesCard(
    long id,
    PublicAuthorView author,
    String slug,
    String title,
    int postCount,
    Instant lastPublishedAt,
    List<SeriesPostRef> posts) {}
