package com.example.short_link.post.application.read;

import java.util.List;

public record PublicSeriesListItem(
    long id, String slug, String title, int postCount, List<String> tags) {}
