package com.example.short_link.post.application.read;

import java.util.List;

/** Public series page: the series plus its ordered published posts. */
public record PublicSeriesDetail(
    PublicAuthorView author, PublicSeriesListItem series, List<PublicPostListItem> posts) {}
