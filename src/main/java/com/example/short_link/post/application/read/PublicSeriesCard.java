package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

// postCount and posts are the published posts alone, as clients that predate notes in a series read
// them; itemCount and items take posts and notes together, in series order.
public record PublicSeriesCard(
    long id,
    PublicAuthorView author,
    String slug,
    String title,
    int postCount,
    Instant lastPublishedAt,
    List<SeriesPostRef> posts,
    int itemCount,
    List<SeriesItemPreview> items) {}
