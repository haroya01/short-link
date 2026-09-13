package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

public record PublicSeriesCard(
    long id,
    PublicAuthorView author,
    String slug,
    String title,
    int postCount,
    Instant lastPublishedAt,
    List<SeriesPostRef> posts) {}
