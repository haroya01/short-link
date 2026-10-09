package com.example.short_link.post.application.read;

import java.util.List;

// postCount counts published posts only, as clients that predate notes in a series read it.
public record PublicSeriesListItem(
    long id, String slug, String title, int postCount, int itemCount, List<String> tags) {}
