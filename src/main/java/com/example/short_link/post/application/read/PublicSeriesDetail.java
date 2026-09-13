package com.example.short_link.post.application.read;

import java.util.List;

public record PublicSeriesDetail(
    PublicAuthorView author, PublicSeriesListItem series, List<PublicPostListItem> posts) {}
